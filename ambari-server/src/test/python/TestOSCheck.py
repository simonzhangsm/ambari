# !/usr/bin/env python

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

from unittest import TestCase
import sys
import tempfile

from common_functions import OSCheck
from mock import patch

from ambari_server import os_check_type


with patch("platform.linux_distribution", return_value=('Suse', '11', 'Final')):
  # We have to use this import HACK because the filename contains a dash
  ambari_server = __import__('ambari-server')


class TestOSCheck(TestCase):
  
  @patch("platform.system")
  @patch("os.path.exists")
  def test_get_os_type(self, mock_exists, mock_system):

    # 1 - Any system
    mock_exists.return_value = False
    mock_system.return_value = 'my_os'
    result = OSCheck.get_os_type()
    self.assertEqual(result, 'my_os')

    # 2 - Negative case
    mock_system.return_value = ('')
    try:
      result = OSCheck.get_os_type()
    except Exception as e:
      # Expected
      self.assertEqual("Cannot detect os type. Exiting...", str(e))
      pass

    # 3 - path exist: '/etc/oracle-release'
    mock_exists.return_value = True
    mock_system.return_value = 'some_os'
    result = OSCheck.get_os_type()
    self.assertEqual(result, 'oraclelinux')

    # 4 - Common system
    mock_exists.return_value = False
    mock_system.return_value = 'CenToS'
    result = OSCheck.get_os_type()
    self.assertEqual(result, 'centos')

  @patch("platform.system")
  @patch("os.path.exists")
  def test_get_os_family(self, mock_exists, mock_system):

    # 1 - Any system
    mock_exists.return_value = False
    mock_system.return_value = 'MY_os'
    result = OSCheck.get_os_family()
    self.assertEqual(result, 'my_os')

    # 2 - Redhat
    mock_exists.return_value = False
    mock_system.return_value = 'Centos Linux'
    result = OSCheck.get_os_family()
    self.assertEqual(result, 'redhat')

    # 3 - Debian
    mock_exists.return_value = False
    mock_system.return_value = 'Ubuntu'
    result = OSCheck.get_os_family()
    self.assertEqual(result, 'debian')

    # 4 - Suse
    mock_exists.return_value = False
    mock_system.return_value = 'suse linux enterprise server'
    result = OSCheck.get_os_family()
    self.assertEqual(result, 'suse')

    mock_exists.return_value = False
    mock_system.return_value = 'SLED'
    result = OSCheck.get_os_family()
    self.assertEqual(result, 'suse')

    # 5 - Negative case
    mock_system.return_value = ''
    try:
      result = OSCheck.get_os_family()
      self.fail("Should throw exception in OSCheck.get_os_family()")
    except Exception as e:
      # Expected
      self.assertEqual("Cannot detect os type. Exiting...", str(e))
      pass


  @patch("platform.release")
  def test_get_os_version(self, mock_linux_distribution):

    # 1 - Any system
    mock_linux_distribution.return_value = '123.45'
    result = OSCheck.get_os_version()
    self.assertEqual(result, '123.45')

    # 2 - Negative case
    mock_linux_distribution.return_value = ''
    try:
      result = OSCheck.get_os_version()
      self.fail("Should throw exception in OSCheck.get_os_version()")
    except Exception as e:
      # Expected
      self.assertEqual("Cannot detect os version. Exiting...", str(e))
      pass


  @patch("platform.release")
  def test_get_os_major_version(self, mock_release):

    # 1
    mock_release.return_value = '123.45.67'
    result = OSCheck.get_os_major_version()
    self.assertEqual(result, '123')

    # 2
    mock_release.return_value = '11'
    result = OSCheck.get_os_major_version()
    self.assertEqual(result, '11')


  @patch.object(OSCheck, "linux_distribution")
  def test_get_os_release_name(self, mock_linux_distribution):

    # 1 - Any system
    mock_linux_distribution.return_value = ('', '', 'MY_NEW_RELEASE')
    result = OSCheck.get_os_release_name()
    self.assertEquals(result, 'my_new_release')

    # 2 - Negative case
    mock_linux_distribution.return_value = ('aaaa', 'bbbb', '')
    try:
      result = OSCheck.get_os_release_name()
      self.fail("Should throw exception in OSCheck.get_os_release_name()")
    except Exception as e:
      # Expected
      self.assertEquals("Cannot detect os release name. Exiting...", str(e))
      pass


  @patch.object(ambari_server, "get_conf_dir")
  def test_update_ambari_properties_os(self, get_conf_dir_mock):

    properties = ["server.jdbc.user.name=ambari-server\n",
                  "server.jdbc.database=ambari\n",
                  "ambari-server.user=root\n",
                  "server.jdbc.user.name=ambari-server\n",
                  "jdk.name=jdk-6u31-linux-x64.bin\n",
                  "jce.name=jce_policy-6.zip\n",
                  "server.os_type=old_sys_os6\n",
                  "java.home=/usr/jdk64/jdk1.6.0_31\n"]

    ambari_server.OS_FAMILY = "family_of_trolls"
    ambari_server.OS_VERSION = "666"

    get_conf_dir_mock.return_value = '/etc/ambari-server/conf'

    (tf1, fn1) = tempfile.mkstemp()
    (tf2, fn2) = tempfile.mkstemp()
    ambari_server.AMBARI_PROPERTIES_RPMSAVE_FILE = fn1
    ambari_server.AMBARI_PROPERTIES_FILE = fn2

    with open(ambari_server.AMBARI_PROPERTIES_RPMSAVE_FILE, 'w') as f:
      for line in properties:
        f.write(line)

    # Call tested method
    ambari_server.update_ambari_properties()

    with open(ambari_server.AMBARI_PROPERTIES_FILE, 'r') as f:
      ambari_properties_content = f.readlines()

    count = 0
    for line in ambari_properties_content:
      if (not line.startswith('#')):
        count += 1
        if (line == "server.os_type=old_sys_os6\n"):
          self.fail("line=" + line)
        else:
          pass

    self.assertEqual(count, 8)
    # Command should not fail if *.rpmsave file is missing
    result = ambari_server.update_ambari_properties()
    self.assertEqual(result, 0)

  @patch("platform.release")
  @patch("platform.system")
  def test_os_type_check(self, mock_system, mock_release):

    # 1 - server and agent os compatible
    mock_release.return_value = '11'
    mock_system.return_value = 'aaa'
    base_args = ["os_check_type.py", "aaa11"]
    sys.argv = list(base_args)

    try:
      os_check_type.main()
    except SystemExit as e:
      # exit_code=0
      self.assertEqual("0", str(e))

    # 2 - server and agent os is not compatible
    mock_system.return_value = 'ddd'
    mock_release.return_value = '33'
    base_args = ["os_check_type.py", "zzz_x77"]
    sys.argv = list(base_args)

    try:
      os_check_type.main()
      self.fail("Must fail because os's not compatible.")
    except Exception as e:
      self.assertEqual(
        "Local OS is not compatible with cluster primary OS. Please perform manual bootstrap on this host.",
        str(e))
      pass

  @patch.object(OSCheck, "get_os_family")
  def test_is_debian_family(self, get_os_family_mock):

    get_os_family_mock.return_value = "debian"
    self.assertEqual(OSCheck.is_debian_family(), True)

    get_os_family_mock.return_value = "troll_os"
    self.assertEqual(OSCheck.is_debian_family(), False)

  @patch.object(OSCheck, "get_os_family")
  def test_is_suse_family(self, get_os_family_mock):

    get_os_family_mock.return_value = "suse"
    self.assertEqual(OSCheck.is_suse_family(), True)

    get_os_family_mock.return_value = "troll_os"
    self.assertEqual(OSCheck.is_suse_family(), False)

  @patch.object(OSCheck, "get_os_family")
  def test_is_redhat_family(self, get_os_family_mock):

    get_os_family_mock.return_value = "redhat"
    self.assertEqual(OSCheck.is_redhat_family(), True)

    get_os_family_mock.return_value = "troll_os"
    self.assertEqual(OSCheck.is_redhat_family(), False)
