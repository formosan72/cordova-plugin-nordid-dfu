#import <Cordova/CDV.h>
#import <UIKit/UIKit.h>

@interface DFUImpl : CDVPlugin

- (void)startDFU:(CDVInvokedUrlCommand*)command;

@end
