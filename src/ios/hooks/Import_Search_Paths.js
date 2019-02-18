
'use strict';

module.exports = function(context) {
    const xcode = context.requireCordovaModule('xcode'),
            fs = require('fs'),
            path = require('path');
    if(process.length >=5 && process.argv[1].indexOf('cordova') == -1) {
        if(process.argv[4] != 'ios') {
            return; // plugin only meant to work for ios platform.
        }
    }

    function fromDir(startPath,filter, rec, multiple){
        if (!fs.existsSync(startPath)){
            console.log("no dir ", startPath);
            return;
        }

        const files=fs.readdirSync(startPath);
        var resultFiles = []
        for(var i=0;i<files.length;i++){
            var filename=path.join(startPath,files[i]);
            var stat = fs.lstatSync(filename);
            if (stat.isDirectory() && rec){
                fromDir(filename,filter); //recurse
            }

            if (filename.indexOf(filter)>=0) {
                if (multiple) {
                    resultFiles.push(filename);
                } else {
                    return filename;
                }
            }
        }
        if(multiple) {
            return resultFiles;
        }
    }
    
    const xcodeProjPath = fromDir('platforms/ios','.xcodeproj', false);
    const projectPath = xcodeProjPath + '/project.pbxproj';
    const myProj = xcode.project(projectPath);
    console.log("xcodeProjPath  "+xcodeProjPath);
    console.log("projectPath  "+projectPath);

    myProj.parseSync();
    // unquote (remove trailing ")
    var projectName = myProj.getFirstTarget().firstTarget.name;
    if(projectName.charAt(0) === '"') {
        projectName = myProj.getFirstTarget().firstTarget.name.substr(1);
        console.log("projectName before trim  "+myProj.getFirstTarget().firstTarget.name);
        if(projectName.charAt(projectName.length-1) === '"')
            projectName = projectName.substr(0, projectName.length-1); //Removing the char " at beginning and the end.
        console.log("projectName  "+projectName);
    } 
    console.log('final project name  '+projectName);
    var projectRoot = context.opts.projectRoot;
    console.log("project root  "+projectRoot);


    function addImportSearchBuildProperty(proj, build) {
       const SWIFT_INCLUDE_PATHS =  proj.getBuildProperty("SWIFT_INCLUDE_PATHS", build);
       const IMPORT_SEARCH_PATH = path.join(projectRoot,'platforms/ios/'+projectName+"/Plugins/cordova-plugin-nordic-dfu");
       console.log("SWIFT_INCLUDE_PATHS  "+SWIFT_INCLUDE_PATHS);
       console.log("IMPORT_SEARCH_PATH  "+IMPORT_SEARCH_PATH);
       if(!SWIFT_INCLUDE_PATHS) {
          proj.addBuildProperty("SWIFT_INCLUDE_PATHS", IMPORT_SEARCH_PATH, build);
       } else {
          var newValue = SWIFT_INCLUDE_PATHS.substr(0,SWIFT_INCLUDE_PATHS.length-1);
          newValue = IMPORT_SEARCH_PATH;
          proj.updateBuildProperty("SWIFT_INCLUDE_PATHS", newValue, build);
       }
    }

    myProj.parseSync();
    addImportSearchBuildProperty(myProj, "Debug");
    addImportSearchBuildProperty(myProj, "Release");

    function updateBridgeHeader() {
        var data = "#import "+'"'+"IntelHex2BinConverter.h"+'"';

        fs.readFile('platforms/ios/'+projectName+"/"+"Bridging-Header.h", (err, readData) => {
            if (err) throw err;
            var value = '';
            for (var i = 0; i < readData.byteLength; i++) {
                value = value + String.fromCharCode(readData[i]);
            }
            if(value.indexOf(data) === -1) {
                fs.appendFile('platforms/ios/'+projectName+"/"+"Bridging-Header.h", data, (err) => {
                    if (err) throw err;
                    console.log('The "data to append" was appended to Bridging-Header.h');
                });
            }
        });        
    }
    updateBridgeHeader();

    function updateDFUImpl() {
        var data = "#import "+'"'+projectName+"-Swift.h"+'"'+'\n';
        

        fs.readFile('platforms/ios/'+projectName+"/Plugins/cordova-plugin-nordic-dfu/"+"DFUImpl.m", (err, readData) => {
            if (err) throw err;
            var value = '';
            for (var i = 0; i < readData.byteLength; i++) {
                value = value + String.fromCharCode(readData[i]);
            }
            
            var writeData = data+value;
            var fd = fs.openSync('platforms/ios/'+projectName+"/Plugins/cordova-plugin-nordic-dfu/"+"DFUImpl.m", 'w+');
            if(value.indexOf(data) === -1) {
                fs.write(fd, writeData,0, (err) => {
                    if (err) throw err;
                    console.log('The "data" was appended to DFUImpl');
                });
            }
        });        
    }

    updateDFUImpl();
    
    fs.writeFileSync(projectPath, myProj.writeSync());
};


