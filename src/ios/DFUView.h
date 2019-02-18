//
//  DFUView.h
//
//
//  Created by Surya on 7/3/17.
//
//

#import <UIKit/UIKit.h>

@interface DFUView : UIView {
    UIView *popup;
    UIView *overlayView;
    UILabel *statusLabel;
    UILabel *progressLabel;
    UIProgressView *progressView;
    UIAlertView *progressAlertView;
    BOOL isProgressViewRemoved;
}

@property (nonatomic, assign) BOOL displayingProgress;

- (void) showOverlay: (NSString *) text;
- (void) showProgressView;
- (void) updateProgressView: (float) value;
- (void) removeOverlay;

@end

