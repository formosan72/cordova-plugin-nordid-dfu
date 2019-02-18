This plugin provides dfu implementation to update the boards

Installation
```
cordova plugin add cordova-plugin-nordic-dfu
```

Need to add file permission for android MarshMallow from your MainActivity.java
```
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
	if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
	}
}
```

Add the url to the file for the app hex file for server file.
```
<!-- Add the url based on the device version. For Ex:- for device version is A10/A20 variant is AX0
for B10/B20 variant is BX0 and For B30 varaiant is B30 -->
Need to place version.json along with zip file on server .

<preference name="variant 1" value="zip file url"/>
<preference name="variant 2" value="zip file url"/>
<preference name="variant 3" value="zip file url"/>
```

Add the file for taking local files.
```
Need to Place zip file and version.json on Resource folder

<preference name="fileName" value="zip file url"/>
```



Add this code to the Javascript side.
```

var updateMessage = {};
updateMessage.uuid = ConnectedPeripheralUUID;
updateMessage.swVersion = "software version from device";
updateMessage.hwVersion = "hardware version from device";
dfuimpl.request('startDFU', JSON.stringify(updateMessage), function(){
     console.log('update success');
     }, function(){
     console.log('update fail');
});
 

```
