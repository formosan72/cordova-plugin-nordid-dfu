#import "Öivita-Swift.h"
#import "OÌivita-Swift.h"
#import "OÃÂivita-Swift.h"
#import "DFUImpl.h"
#import "DFUView.h"
#import "Reachability.h"
#import <CoreBluetooth/CoreBluetooth.h>

@interface DFUImpl () <CBCentralManagerDelegate,CBPeripheralDelegate, DFUServiceDelegate, DFUProgressDelegate, LoggerDelegate, UIAlertViewDelegate>{
    //MARK: - Class Properties
    int firmwareSize;
    int totalBytesTranser;
    DFUView *mUpdateView;
    NSString *dfuCallbackId;
    NSDictionary *requestDict;
    NSInteger version;
}
@end

@implementation DFUImpl

- (void)startDFU:(CDVInvokedUrlCommand*)command
{
    mUpdateView = [[DFUView alloc] init];
    mUpdateView.hidden = YES;
    [self.webView addSubview:mUpdateView];
    
    [self.commandDelegate runInBackground:^{
        dfuCallbackId = [command callbackId];
        firmwareSize = 0;
        totalBytesTranser = 0;
        NSInteger lastBuildVersion =1;
        
        NSString* request = [[command arguments] objectAtIndex:0];
        NSLog(@"DFU Request <<<<<<<<<< : %@",request);
        NSData *stringValueData = [request dataUsingEncoding:NSUTF8StringEncoding];
        
        NSError *jsonError = nil;
        id requests = [NSJSONSerialization JSONObjectWithData:stringValueData options:0 error:&jsonError];
        
        if([requests isKindOfClass:[NSDictionary class]])
        {
            requests = [NSArray arrayWithObject:requests];
            requestDict = [requests objectAtIndex:0];
            if([requestDict valueForKey:@"swVersion"])
                lastBuildVersion  = [[requestDict valueForKey:@"swVersion"] integerValue];
            
            //we have to get the fileurl from config.xml
            if([self.commandDelegate.settings objectForKey:[@"fileUrl" lowercaseString]]) {
                NSString *fileUrl = [self.commandDelegate.settings objectForKey:[@"fileUrl" lowercaseString]];
                //server url path to get the zip file and version.json
                if([fileUrl hasPrefix:@"http"]) {
                    Reachability *reachability = [Reachability reachabilityWithHostName:@"www.google.com"];
                    if([reachability currentReachabilityStatus] == NotReachable) {
                        return ;
                    } else {
                        NSURL *url = [NSURL URLWithString:fileUrl];
                        NSURL *versionUrl = [url URLByDeletingLastPathComponent];
                        versionUrl = [versionUrl URLByAppendingPathComponent:@"version.json"];
                        NSData *data = [NSData dataWithContentsOfURL:versionUrl];
                        if(data != nil) {
                            NSError *error;
                            NSDictionary *jsonDict = [NSJSONSerialization JSONObjectWithData:data options:kNilOptions error:&error];
                            version = [[jsonDict valueForKey:@"date"] integerValue];
                        }
                    }
                } else {
                    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
                    NSString *path = [paths objectAtIndex:0];

                    NSString *jsonDataPath = [path stringByAppendingPathComponent:@"version.json"];
                    NSData *data = [NSData dataWithContentsOfFile:jsonDataPath];
                    
                    if(data != nil) {
                        NSError *error;
                        NSDictionary *jsonDict = [NSJSONSerialization JSONObjectWithData:data options:kNilOptions error:&error];
                        version = [[jsonDict valueForKey:@"date"] integerValue];
                    }
                }
                
                if(lastBuildVersion == 1) {
                    mUpdateView.hidden = NO;
                    [self.commandDelegate runInBackground:^{
                        [self showOverlay];
                        [self downloadZipFile];
                    }];
                } else if(version > lastBuildVersion) {
                    dispatch_async(dispatch_get_main_queue(), ^{
                        UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"" message:@"New update available. Would you like to update your device?" delegate:self cancelButtonTitle:@"NO" otherButtonTitles:@"YES", nil];
                        alert.delegate = self;
                        [alert show];
                    });
                }
            }
        }
    }];
}

#pragma MARK AlertView Delegates
- (void)alertView:(UIAlertView *)alertView clickedButtonAtIndex:(NSInteger)buttonIndex {
    if(buttonIndex == YES) {
        mUpdateView.hidden = NO;
        [self.commandDelegate runInBackground:^{
            [self showOverlay];
            [self downloadZipFile ];
        }];
    }
}

-(void)downloadZipFile
{
    NSString *fileUrl  = [self.commandDelegate.settings objectForKey:[@"fileUrl" lowercaseString]];
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString *documentsPath = [paths objectAtIndex:0];
    //download file
    dispatch_queue_t queue = dispatch_get_global_queue(0,0);
    dispatch_async(queue, ^{
        if(fileUrl && [fileUrl hasPrefix:@"http"]){
            
                NSLog(@"Beginning download");
                Reachability *reachability = [Reachability reachabilityWithHostName:@"www.google.com"];
                if([reachability currentReachabilityStatus] == NotReachable) {
                    dispatch_async(dispatch_get_main_queue(), ^{
                        UIAlertView *networkAlert = [[UIAlertView alloc] initWithTitle:@"No Network" message:@"There is no network, which is required to update firmware." delegate:self cancelButtonTitle:@"Ok" otherButtonTitles:nil, nil];
                        [networkAlert show];
                    });
                    return ;
                } else {
                    //Download the zip file from server and save it to docuuments folder
                    NSURL *url = [NSURL URLWithString:fileUrl];
                    NSData *urlData = [NSData dataWithContentsOfURL:url];
                
                    //Find a cache directory. You could consider using documenets dir instead (depends on the data you are fetching)
                    NSLog(@"Got the data!");
                
                    //Save the data
                    NSLog(@"Saving");
                    NSString *dataPath = [documentsPath stringByAppendingPathComponent:[url lastPathComponent]];
                    dataPath = [dataPath stringByStandardizingPath];
                    [urlData writeToFile:dataPath atomically:YES];
                    //go to dfu
                    [self startDFUprocess:dataPath];
                }
        } else {
                NSString *dataPath = [documentsPath stringByAppendingPathComponent:fileUrl];
                [self startDFUprocess:dataPath];
        }
    });
}

-(void) startDFUprocess:(NSString *)filePath {
    [[UIApplication sharedApplication] setIdleTimerDisabled:YES];
    NSString *device_uuid = [requestDict valueForKey:@"uuid"];
    // Setting up the File
    
    DFUFirmware *selectedFirmware = [[DFUFirmware alloc] initWithUrlToZipFile:[NSURL fileURLWithPath:filePath]];
    DFUFirmwareSize *dfuFirmwareSize = selectedFirmware.size;
    
    firmwareSize = dfuFirmwareSize.softdevice+dfuFirmwareSize.application+dfuFirmwareSize.bootloader;
    
    // Code to get the connected the devices and getting the particular device which we have connected in JS side
    dispatch_queue_t centralQueue = dispatch_queue_create("com.vensi.centralManager", DISPATCH_QUEUE_SERIAL);
    CBCentralManager *centralManager = [[CBCentralManager alloc]initWithDelegate:self queue:centralQueue];
    centralManager.delegate = self;
    
    NSString *serviceUUID = @"";
    if([requestDict valueForKey:@"dfuServiceUUID"])
        serviceUUID = [requestDict valueForKey:@"dfuServiceUUID"];
    else
        serviceUUID = @"00001530-1212-EFDE-1523-785FEABCD123";
    
    NSString *controlPointChar = [serviceUUID stringByReplacingCharactersInRange:NSMakeRange(7, 1) withString:@"1"];
    NSString *versionChar = [serviceUUID stringByReplacingCharactersInRange:NSMakeRange(7, 1) withString:@"4"];
    NSString *packetChar = [serviceUUID stringByReplacingCharactersInRange:NSMakeRange(7, 1) withString:@"2"];
    
    [[NSUserDefaults standardUserDefaults] setValue:serviceUUID forKey:@"dfuServiceUUID"];
    [[NSUserDefaults standardUserDefaults] setValue:controlPointChar forKey:@"controlPointChar"];
    [[NSUserDefaults standardUserDefaults] setValue:versionChar forKey:@"versionChar"];
    [[NSUserDefaults standardUserDefaults] setValue:packetChar forKey:@"packetChar"];
    
    CBUUID *dfuServiceUUID = [CBUUID UUIDWithString:serviceUUID];
    NSArray *connectedPeripherals = [centralManager retrieveConnectedPeripheralsWithServices:@[dfuServiceUUID]];
    NSLog(@"Connected Peripherals without filter: %lu",(unsigned long)connectedPeripherals.count);
    for (CBPeripheral *connectedPeripheral in connectedPeripherals) {
        NSString *temp_id = [NSString stringWithFormat:@"%@",connectedPeripheral.identifier];
        
        if([temp_id rangeOfString:device_uuid].location != NSNotFound){
            NSLog(@"Connected Peripheral: %@",connectedPeripheral.name);
            DFUServiceInitiator *initiator = [[DFUServiceInitiator alloc] initWithCentralManager: centralManager target:connectedPeripheral];
            initiator = [initiator withFirmwareFile:selectedFirmware];
            initiator.forceDfu = NO; // default NO
            initiator.packetReceiptNotificationParameter = 12; // default is 12
            initiator.logger = self; // - to get log info
            initiator.delegate = self; // - to be informed about current state and errors
            initiator.progressDelegate = self; // - to show progress bar
            
            [initiator start];
        }
    }
}
#pragma mark Showing Overlays
-(void)showOverlay
{
    NSString *message =  [NSString stringWithFormat:@"Preparing device for firmware update \n with version : %ld \n \n This can take up to 5 mins. \n  Please wait...",(long)version];
    [mUpdateView showOverlay:message];
}

//MARK: - CentralManagerDelegate
- (void)centralManagerDidUpdateState:(CBCentralManager *)central
{
    NSLog(@"Got the Central State : %ld", (long)central.state);
}

//MARK: - LoggerDelegate
- (void)logWith:(enum LogLevel)level message:(NSString * _Nonnull)message {
    NSLog(@"state %@", message);
}

//MARK: - DFUServiceDelegate
-(void)didStateChangedTo:(enum DFUState)state
{
    switch (state) {
        case DFUStateSignatureMismatch:
            NSLog(@"DFUStateSignatureMismatch");
            [self clearUI];
            break;
            
        case DFUStateCompleted:
            NSLog(@"DFUStateCompleted");
            [self successFileTransfer];
            [self removeView];
            [self sendResponseToCallback];
            break;
            
        case DFUStateConnecting:
            NSLog(@"DFUStateConnecting");
            break;
            
        case DFUStateDisconnecting:
            NSLog(@"DFUStateDisconnecting");
            break;
            
        case DFUStateEnablingDfuMode:
            NSLog(@"DFUStateEnablingDfuMode");
            break;
            
        case DFUStateStarting:
            NSLog(@"DFUStateStarting");
            break;
            
        case DFUStateUploading:
            NSLog(@"DFUStateUploading");
            break;
            
        case DFUStateValidating:
            NSLog(@"DFUStateValidating");
            break;
            
        case DFUStateOperationNotPermitted:
            NSLog(@"DFUStateOperationNotPermitted");
            [self clearUI];
            break;
            
        case DFUStateFailed:
            NSLog(@"DFUStateFailed");
            [self clearUI];
            break;
            
        case DFUStateAborted:
            [self clearUI];
            NSLog(@"DFUStateAborted");
            break;
    }
}

//MARK: - DFUProgressDelegate
- (void)onUploadProgress:(NSInteger)part totalParts:(NSInteger)totalParts progress:(NSInteger)progress bytesSent:(NSInteger)bytesSent currentSpeedBytesPerSecond:(double)currentSpeedBytesPerSecond avgSpeedBytesPerSecond:(double)avgSpeedBytesPerSecond {
    [self dfuUploadProgress: part :totalParts :progress :bytesSent];
}

- (void)didErrorOccur:(enum DFUError)error withMessage:(NSString * _Nonnull)message {
    [self clearUI];
    NSLog(@"error message %@", message);
}

//MARK: -
-(void)dfuUploadProgress :(NSInteger)currentPart :(NSInteger)totalParts :(NSInteger) progress :(NSInteger)bytesSent{
    
    int currentProgress = 0;
    if(currentPart == 1) {
        totalBytesTranser = (int)bytesSent;
        currentProgress = ((int)totalBytesTranser * 100)/firmwareSize;
    } else {
        int temp = totalBytesTranser + (int)bytesSent;
        currentProgress = ((int)temp * 100)/firmwareSize;
    }
       
    NSLog(@"current part %d and progress %d and percentage %d bytessent %d", currentPart, (int)currentProgress, totalBytesTranser, (int)bytesSent);
    [mUpdateView showProgressView];
    [mUpdateView updateProgressView:currentProgress];
}

-(void)successFileTransfer {
    [[UIApplication sharedApplication] setIdleTimerDisabled:NO];
    dispatch_async(dispatch_get_main_queue(), ^{
        UIAlertView *successAlert = [[UIAlertView alloc] initWithTitle:@"" message:@"Device Updated with the latest Firmware. Please reconnect to the device." delegate:nil cancelButtonTitle:@"OK" otherButtonTitles:nil, nil];
        [successAlert show];
    });
}

-(void)clearUI {
    [self removeView];
    firmwareSize = 0;
    totalBytesTranser = 0;
    dispatch_async(dispatch_get_main_queue(), ^{
        UIAlertView *failedAlert = [[UIAlertView alloc] initWithTitle:@"" message:@"Unable to update the device firmware. Please try again later." delegate:nil cancelButtonTitle:@"OK" otherButtonTitles:nil, nil];
        [failedAlert show];
    });
}

-(void)removeView
{
    [mUpdateView removeOverlay];
    mUpdateView.hidden = YES;
}

-(void)sendResponseToCallback {
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"DFUResponse"];
    result.keepCallback = [NSNumber numberWithInt:1];
    [self.commandDelegate sendPluginResult:result callbackId:dfuCallbackId];
}

@end
