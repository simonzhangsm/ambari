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
var lazyloading = require('utils/lazy_loading');

App.MainChartsHeatmapRackView = Em.View.extend({
  templateName: require('templates/main/charts/heatmap/heatmap_rack'),
  classNames: ['rack'],
  classNameBindings: ['visualSchema'],

  /** rack status block class */
  statusIndicator:'statusIndicator',
  /** loaded hosts of rack */
  hosts: [],

  willInsertElement: function () {
    this.set('hosts', []);
  },

  didInsertElement: function () {
    var rackHosts = this.get('rack.hosts').toArray();
    if (rackHosts.length > 100) {
      lazyloading.run({
        destination: this.get('hosts'),
        source: rackHosts,
        context: this.get('rack'),
        initSize: 25,
        chunkSize: 100,
        delay: 25
      });
    } else {
      this.set('hosts', rackHosts);
      this.set('rack.isLoaded', true);
    }
  },
  /**
   * Provides the CSS style for an individual host.
   * This can be used as the 'style' attribute of element.
   */
  hostCssStyle: function () {
    var rack = this.get('rack');
    var widthPercent = 100;
    var hostCount = rack.get('hosts.length');
    if (hostCount && hostCount < 11) {
      widthPercent = (100 / hostCount) - 0.5;
    } else {
      widthPercent = 10; // max out at 10%
    }
    return "width:" + widthPercent + "%;float:left;";
  }.property('rack')
});