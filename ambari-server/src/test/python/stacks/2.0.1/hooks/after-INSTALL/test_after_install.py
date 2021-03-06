#!/usr/bin/env python

'''
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
'''

from mock import MagicMock, call, patch

from stacks.utils.RMFTestCase import *


@patch("os.path.exists", new=MagicMock(return_value=True))
class TestHookAfterInstall(RMFTestCase):
  def test_hook_default(self):
    self.executeScript("1.3.2/hooks/after-INSTALL/scripts/hook.py",
                       classname="AfterInstallHook",
                       command="hook",
                       config_file="default.json"
    )
    self.assertResourceCalled('Directory', '/etc/hadoop/conf',
                              owner='root',
                              group='root',
                              recursive=True,
                              )
    self.assertResourceCalled('File', '/etc/hadoop/conf/hadoop-env.sh',
                              content=Template('hadoop-env.sh.j2'),
                              owner='hdfs',
                              )
    self.assertResourceCalled('XmlConfig', 'core-site.xml',
                              owner='hdfs',
                              group='hadoop',
                              conf_dir='/etc/hadoop/conf',
                              configurations=self.getConfig()['configurations']['core-site'],
                              )
    self.assertNoMoreResources()
