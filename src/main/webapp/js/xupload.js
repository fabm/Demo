"use strict";

var app = angular.module('fileUpload', [ 'angularFileUpload' ]);
var uploaded;


var MyCtrl = function($scope, $http, $timeout, $upload) {
    $scope.isLoaded = false;
    var loadFile;
    $scope.onFileSelect = function($files){
        var fr = new FileReader();
        loadFile = $files[0]
            $scope.isLoaded = true;
        //fr.readAsDataURL(loadFile);
        fr.onload = function(e){
            //$scope.data = fr.result;
            $scope.$digest();
        }
    }
    $scope.confirm = function(){
        $scope.isLoaded=false;

        var ajax = new XMLHttpRequest();
        ajax.open("GET",'/upurl',false);
        console.log(loadFile);
        ajax.onload = function(){
            var uploadUrl = ajax.response;
            			$upload.upload({
            				url : uploadUrl,
            				method: $scope.httpMethod,
            				headers: {'my-header': 'my-header-value'},
            				file: loadFile,
            				fileFormDataName: 'myFile'
            			}).then(function(response) {
            				console.log(response);
            			}, function(response) {
            				console.log(response);
            			}, function(evt) {
            				console.log(evt);
            			}).xhr(function(xhr){
                            console.log(xhr);
            			});
        }
        ajax.send();
    }
};
