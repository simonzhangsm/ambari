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
import resource_management.core.source


@patch.object(resource_management.core.source, "InlineTemplate", new=MagicMock(return_value='InlineTemplateMock'))
class TestFlumeHandler(RMFTestCase):

  def test_configure_default(self):
    self.executeScript("2.0.6/services/FLUME/package/scripts/flume_handler.py",
                       classname="FlumeHandler",
                       command="configure",
                       config_file="default.json")

    self.assert_configure_default()
    self.assertNoMoreResources()

  @patch("os.path.isfile")
  def test_start_default(self, os_path_isfile_mock):
    # 1st call is to check if the conf file is there - that should be True
    # 2nd call is to check if the process is live - that should be False
    os_path_isfile_mock.side_effect = [True, False]

    self.executeScript("2.0.6/services/FLUME/package/scripts/flume_handler.py",
                       classname="FlumeHandler",
                       command="start",
                       config_file="default.json")

    self.assert_configure_default()

    self.assertResourceCalled('Execute', format('env JAVA_HOME=/usr/jdk64/jdk1.7.0_45 /usr/bin/flume-ng agent '
      '--name a1 '
      '--conf /etc/flume/conf/a1 '
      '--conf-file /etc/flume/conf/a1/flume.conf '
      '-Dflume.monitoring.type=ganglia '
      '-Dflume.monitoring.hosts=c6401.ambari.apache.org:8655'),
      wait_for_finish=False)

    self.assertResourceCalled('Execute', 'pgrep -o -f /etc/flume/conf/a1/flume.conf > /var/run/flume/a1.pid',
      logoutput=True,
      tries=5,
      try_sleep=10)

    self.assertNoMoreResources()

  @patch("glob.glob")
  def test_stop_default(self, glob_mock):
    glob_mock.return_value = ['/var/run/flume/a1.pid']

    self.executeScript("2.0.6/services/FLUME/package/scripts/flume_handler.py",
                       classname="FlumeHandler",
                       command="stop",
                       config_file="default.json")

    self.assertTrue(glob_mock.called)

    self.assertResourceCalled('Execute', 'kill `cat /var/run/flume/a1.pid` > /dev/null 2>&1',
      ignore_failures=True)

    self.assertResourceCalled('File', '/var/run/flume/a1.pid', action=['delete'])

    self.assertNoMoreResources()

  @patch("resource_management.libraries.script.Script.put_structured_out")
  @patch("sys.exit")
  def test_status_default(self, sys_exit_mock, structured_out_mock):
    
    try:
      self.executeScript("2.0.6/services/FLUME/package/scripts/flume_handler.py",
                       classname="FlumeHandler",
                       command="status",
                       config_file="default.json")
    except:
      # expected since ComponentIsNotRunning gets raised
      pass
    
    # test that the method was called with empty processes
    self.assertTrue(structured_out_mock.called)
    structured_out_mock.assert_called_with({'processes': []})

    self.assertNoMoreResources()

  @patch("resource_management.libraries.script.Script.put_structured_out")
  @patch("glob.glob")
  @patch("sys.exit")
  def test_status_with_result(self, sys_exit_mock, glob_mock, structured_out_mock):
    glob_mock.return_value = ['/etc/flume/conf/a1/ambari-meta.json']

    try:
      self.executeScript("2.0.6/services/FLUME/package/scripts/flume_handler.py",
                       classname="FlumeHandler",
                       command="status",
                       config_file="default.json")
    except:
      # expected since ComponentIsNotRunning gets raised
      pass
    
    self.assertTrue(structured_out_mock.called)

    structured_out_mock.assert_called_with({'processes':
      [{'status': 'NOT_RUNNING', 'channels_count': 0, 'sinks_count': 0,
        'name': 'a1', 'sources_count': 0}]})

    self.assertNoMoreResources()

  def assert_configure_default(self):

    self.assertResourceCalled('Directory', '/etc/flume/conf')

    self.assertResourceCalled('Directory', '/var/log/flume', owner='flume')

    self.assertResourceCalled('Directory', '/etc/flume/conf/a1')

    self.assertResourceCalled('PropertiesFile', '/etc/flume/conf/a1/flume.conf',
      mode=0o644,
      properties=build_flume(
        self.getConfig()['configurations']['flume-conf']['content'])['a1'])

    self.assertResourceCalled('File',
      '/etc/flume/conf/a1/log4j.properties',
      content=Template('log4j.properties.j2', agent_name='a1'),
      mode=0o644)

    self.assertResourceCalled('File',
      '/etc/flume/conf/a1/ambari-meta.json',
      content='{"channels_count": 1, "sinks_count": 1, "sources_count": 1}',
      mode=0o644)

  def assert_configure_many(self):

    self.assertResourceCalled('Directory', '/etc/flume/conf')

    self.assertResourceCalled('Directory', '/var/log/flume', owner='flume')

    top = build_flume(self.getConfig()['configurations']['flume-conf']['content'])

    # a1
    self.assertResourceCalled('Directory', '/etc/flume/conf/a1')
    self.assertResourceCalled('PropertiesFile', '/etc/flume/conf/a1/flume.conf',
      mode=0o644,
      properties=top['a1'])
    self.assertResourceCalled('File',
      '/etc/flume/conf/a1/log4j.properties',
      content=Template('log4j.properties.j2', agent_name='a1'),
      mode=0o644)
    self.assertResourceCalled('File',
      '/etc/flume/conf/a1/ambari-meta.json',
      content='{"channels_count": 1, "sinks_count": 1, "sources_count": 1}',
      mode=0o644)

    # b1
    self.assertResourceCalled('Directory', '/etc/flume/conf/b1')
    self.assertResourceCalled('PropertiesFile', '/etc/flume/conf/b1/flume.conf',
      mode=0o644,
      properties=top['b1'])
    self.assertResourceCalled('File',
      '/etc/flume/conf/b1/log4j.properties',
      content=Template('log4j.properties.j2', agent_name='b1'),
      mode=0o644)
    self.assertResourceCalled('File',
      '/etc/flume/conf/b1/ambari-meta.json',
      content='{"channels_count": 1, "sinks_count": 1, "sources_count": 1}',
      mode=0o644)

  @patch("os.path.isfile")
  def test_start_single(self, os_path_isfile_mock):
    # 1st call is to check if the conf file is there - that should be True
    # 2nd call is to check if the process is live - that should be False
    os_path_isfile_mock.side_effect = [True, False]

    self.executeScript("2.0.6/services/FLUME/package/scripts/flume_handler.py",
                       classname="FlumeHandler",
                       command="start",
                       config_file="flume_target.json")

    self.assert_configure_many()

    self.assertResourceCalled('Execute', format('env JAVA_HOME=/usr/jdk64/jdk1.7.0_45 /usr/bin/flume-ng agent '
      '--name b1 '
      '--conf /etc/flume/conf/b1 '
      '--conf-file /etc/flume/conf/b1/flume.conf '
      '-Dflume.monitoring.type=ganglia '
      '-Dflume.monitoring.hosts=c6401.ambari.apache.org:8655'),
      wait_for_finish=False)

    self.assertResourceCalled('Execute', 'pgrep -o -f /etc/flume/conf/b1/flume.conf > /var/run/flume/b1.pid',
      logoutput=True,
      tries=5,
      try_sleep=10)

    self.assertNoMoreResources()

  @patch("os.path.isfile")
  def test_start_single(self, os_path_isfile_mock):
    # 1st call is to check if the conf file is there - that should be True
    # 2nd call is to check if the process is live - that should be False
    os_path_isfile_mock.side_effect = [True, False]

    self.executeScript("2.0.6/services/FLUME/package/scripts/flume_handler.py",
                       classname="FlumeHandler",
                       command="start",
                       config_file="flume_target.json")

    self.assert_configure_many()

    self.assertResourceCalled('Execute', format('env JAVA_HOME=/usr/jdk64/jdk1.7.0_45 /usr/bin/flume-ng agent '
      '--name b1 '
      '--conf /etc/flume/conf/b1 '
      '--conf-file /etc/flume/conf/b1/flume.conf '
      '-Dflume.monitoring.type=ganglia '
      '-Dflume.monitoring.hosts=c6401.ambari.apache.org:8655'),
      wait_for_finish=False)

    self.assertResourceCalled('Execute', 'pgrep -o -f /etc/flume/conf/b1/flume.conf > /var/run/flume/b1.pid',
      logoutput=True,
      tries=5,
      try_sleep=10)

    self.assertNoMoreResources()

  @patch("glob.glob")
  def test_stop_single(self, glob_mock):
    glob_mock.return_value = ['/var/run/flume/b1.pid']

    self.executeScript("2.0.6/services/FLUME/package/scripts/flume_handler.py",
                       classname="FlumeHandler",
                       command="stop",
                       config_file="flume_target.json")

    self.assertTrue(glob_mock.called)

    self.assertResourceCalled('Execute', 'kill `cat /var/run/flume/b1.pid` > /dev/null 2>&1',
      ignore_failures=True)

    self.assertResourceCalled('File', '/var/run/flume/b1.pid', action=['delete'])

    self.assertNoMoreResources()

def build_flume(content):
  result = {}
  agent_names = []

  for line in content.split('\n'):
    rline = line.strip()
    if 0 != len(rline) and not rline.startswith('#'):
      pair = rline.split('=')
      lhs = pair[0].strip()
      rhs = pair[1].strip()

      part0 = lhs.split('.')[0]

      if lhs.endswith(".sources"):
        agent_names.append(part0)

      if part0 not in result:
        result[part0] = {}

      result[part0][lhs] = rhs

  # trim out non-agents
  for k in list(result.keys()):
    if not k in agent_names:
      del result[k]


  return result
