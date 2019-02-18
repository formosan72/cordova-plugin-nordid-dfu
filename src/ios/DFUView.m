//
//  DFUView.m
//
//
//  Created by Surya on 7/3/17.
//
//


#import "DFUView.h"

@implementation DFUView

- (id) init {
    isProgressViewRemoved = true;
    CGRect screenRect = [[UIScreen mainScreen] bounds];
    return [self initWithFrame: CGRectMake(0, 0, screenRect.size.width, screenRect.size.height)];
}

- (id) initWithFrame:(CGRect)frame {
     [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(rotated:) name:UIDeviceOrientationDidChangeNotification object:nil];
    if (self = [super initWithFrame: frame]) {
        CGRect screenRect = [[UIScreen mainScreen] bounds];
        overlayView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, screenRect.size.width, screenRect.size.height)];
        overlayView.tag = 100;
        [overlayView setBackgroundColor: [self colorWithHexString:@"333333"]];
        overlayView.layer.opacity = 0.9f;
        
        [overlayView addSubview:statusLabel];
        
        [self addSubview: overlayView];
    }
    
    return self;
}

- (void) showOverlay: (NSString *) text {
    dispatch_async(dispatch_get_main_queue(), ^{
        CGRect screenRect = [[UIScreen mainScreen] bounds];
        
        statusLabel = [[UILabel alloc] initWithFrame:CGRectMake(0, 0, screenRect.size.width, screenRect.size.height)];
        [statusLabel setTextColor:[UIColor whiteColor]];
        [statusLabel setTextAlignment:NSTextAlignmentCenter];
        
        if([UIDevice currentDevice].userInterfaceIdiom == UIUserInterfaceIdiomPhone) {
            [statusLabel setFont:[UIFont systemFontOfSize:18]];
        } else if([UIDevice currentDevice].userInterfaceIdiom == UIUserInterfaceIdiomPad) {
            [statusLabel setFont:[UIFont systemFontOfSize:22]];
        }
        
        statusLabel.numberOfLines = 5;
        statusLabel.lineBreakMode = NSLineBreakByWordWrapping;
        statusLabel.text =  text;
        
        [overlayView addSubview:statusLabel];
    });
}

- (void) showProgressView {
    if(isProgressViewRemoved) {
        isProgressViewRemoved = false;
    dispatch_async(dispatch_get_main_queue(), ^{
        _displayingProgress = YES;
        [statusLabel removeFromSuperview];
        
        popup = [[UIView alloc] initWithFrame:CGRectMake(0, 0, 100, 50)];
        
        progressView = [[UIProgressView alloc] initWithFrame:CGRectMake(10, 5, 250, 10)];
        progressView.progress = 0.0f;
        progressView.tag = 11;
        [progressView setProgressTintColor:[UIColor blackColor]];
        
        progressLabel = [[UILabel alloc] initWithFrame:CGRectMake(120, 30, 120, 20)];
        [progressLabel setTextColor:[UIColor blackColor]];
        progressLabel.tag = 22;
        progressLabel.text = @"0%";
        
        [popup addSubview:progressView];
        [popup addSubview:progressLabel];
        
        progressAlertView = [[UIAlertView alloc] initWithTitle:@"Updating"
                                                       message:@""
                                                      delegate:self
                                             cancelButtonTitle:nil
                                             otherButtonTitles:nil, nil];
        
        [progressAlertView setValue:popup forKey:@"accessoryView"];
        
        [progressAlertView show];
    });
    }
}

- (void) updateProgressView: (float) value {
    dispatch_async(dispatch_get_main_queue(), ^{
        NSInteger intValue = (int)(value);
        float progressPercentage =((float)intValue/100.0);
        progressLabel.text = [NSString stringWithFormat:@"%ld%%", (long)intValue];
        [progressView setProgress: progressPercentage animated:YES];
    });
}

- (void) removeOverlay {
    isProgressViewRemoved = true;
    dispatch_async(dispatch_get_main_queue(), ^{
        if (_displayingProgress == YES) {
            [progressAlertView dismissWithClickedButtonIndex:0 animated:YES];
        }
        
        [overlayView removeFromSuperview];
    });
}

- (void)rotated:(NSNotification *)notification {
    dispatch_async(dispatch_get_main_queue(), ^{
        CGRect screenRect = [[UIScreen mainScreen] bounds];
        
        if(statusLabel)
            statusLabel.frame = CGRectMake(0, 0, screenRect.size.width, screenRect.size.height);
            
        if(overlayView && popup)
            overlayView.frame = CGRectMake(0, 0, screenRect.size.width, screenRect.size.height);
        
        if(popup)
            popup.frame = CGRectMake(0, 0, 100, 50);
        
    });
}

-(UIColor*)colorWithHexString:(NSString*)hex
{
    NSString *cString = [[hex stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]] uppercaseString];

    // String should be 6 or 8 characters
    if ([cString length] < 6) return [UIColor grayColor];

    // strip 0X if it appears
    if ([cString hasPrefix:@"0X"]) cString = [cString substringFromIndex:2];

    if ([cString length] != 6) return  [UIColor grayColor];

    // Separate into r, g, b substrings
    NSRange range;
    range.location = 0;
    range.length = 2;
    NSString *rString = [cString substringWithRange:range];

    range.location = 2;
    NSString *gString = [cString substringWithRange:range];

    range.location = 4;
    NSString *bString = [cString substringWithRange:range];

    // Scan values
    unsigned int r, g, b;
    [[NSScanner scannerWithString:rString] scanHexInt:&r];
    [[NSScanner scannerWithString:gString] scanHexInt:&g];
    [[NSScanner scannerWithString:bString] scanHexInt:&b];

    return [UIColor colorWithRed:((float) r / 255.0f)
                           green:((float) g / 255.0f)
                            blue:((float) b / 255.0f)
                           alpha:1.0f];
}


@end
