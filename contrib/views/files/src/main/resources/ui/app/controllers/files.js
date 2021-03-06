/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var App = require('app');

App.FilesController = Ember.ArrayController.extend({
  actions:{
    moveFile:function (opt,file) {
      var src, title, self,
          file = file || this.get('selectedFiles.firstObject'),
          moving = this.get('movingFile');

      if (opt == 'cut') {
        src = file.toJSON({includeId: true});
        src = Em.merge(src,{title:file.get('title'),path:file.get('path')})
        this.set('movingFile',src);
      };

      if (opt == 'move') {
        self = this;
        this.store.move(moving.path,[this.get('path'),moving.title].join('/').replace('//','/'))
          .then(function () {
            self.set('movingFile',null);
          });
      };

      if (opt == 'cancel') {
        this.set('movingFile',null);
      };
    },
    deleteFile:function () {
      var self = this;
      var selected = this.get('selectedFiles');
      selected.forEach(function (file) {
        self.store.remove(file);
      });
    },
    download:function (option) {
      var files = this.get('selectedFiles');
      this.store.linkFor(files,option).then(function (link) {
        window.location.href = link;
      });
    },
    mkdir:function (opt) {
      var name,self,newDir;
      if (opt === 'edit') {
        this.set('isMkdir',true);
      };

      if (opt === 'cancel') {
        this.set('newDirName','');
        this.set('isMkdir',false);
      };

      if (opt === 'confirm') {
        self = this;
        name = this.get('newDirName');

        if (Em.isEmpty(name)) {
          return false;
        }
        newDir = [this.get('path'),name].join('/').replace('//','/');

        this.store.mkdir(newDir).then(function () {
          self.set('newDirName','');
          self.set('isMkdir',false);
        });
      };
    },
    upload:function (opt) {
      if (opt === 'open') {
        this.set('isUploading',true);
      };

      if (opt === 'close') {
        this.set('isUploading',false);
      };
    },
    sort:function (pr) {
      var currentProperty = this.get('sortProperties');
      if (pr == currentProperty[0] || pr == 'toggle') {
        this.toggleProperty('sortAscending');
      } else{
        this.set('sortProperties',[pr]);
        this.set('sortAscending',true);
      };
    }
  },
  init:function () {
    var controller = this;
    var adapter = controller.store.adapterFor('file');
    var url = adapter.buildURL('upload');
    this.uploader.set('url',url);
    this.uploader.on('didUpload', function(e) {

      controller.store.pushPayload('file',{file:e});
    });
  },

  sortProperties: ['title'],
  sortAscending: true,

  needs: ["file"],
  movingFile:null,
  uploader:App.Uploader,
  isRemoving:false,
  isMkdir:false,
  isUploading:false,
  newDirName:'',
  queryParams: ['path'],
  path: '/',
  hideMoving:function () {
    return (this.movingFile)?[this.path,this.movingFile.title].join('/').replace('//','/')===this.movingFile.path:false;
  }.property('movingFile','path'),
  currentDir:function () {
    var splitpath = this.get('path').split('/');
    return splitpath.get(splitpath.length-1) || '/';
  }.property('path'),
  selectedOne:function () {
    return this.get('selectedFiles.length') == 1;
  }.property('selectedFiles'),
  isSelected:function () {
    return this.get('selectedFiles.length') > 0;
  }.property('selectedFiles'),
  selectedFiles:function () {
    return this.get('content').filterProperty('selected',true);
  }.property('content.@each.selected'),
  canConcat:function () {
    return this.get('selectedFiles').filterProperty('isDirectory').get('length')==0;
  }.property('selectedFiles'),
  fileList:function () {
    return this.get('arrangedContent');
  }.property('arrangedContent')
});

App.FilesAlertController = Em.ObjectController.extend({
  content:null
});
