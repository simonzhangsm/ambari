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
from mock import patch, MagicMock

from resource_management.core import Environment, Fail
from resource_management.core.system import System
from resource_management.core.resources.system import Link

import os

@patch.object(System, "os_family", new='redhat')
class TestLinkResource(TestCase):

  @patch.object(os.path, "realpath")
  @patch.object(os.path, "lexists")
  @patch.object(os.path, "islink")
  @patch.object(os, "unlink")
  @patch.object(os, "symlink")
  def test_action_create_relink(self, symlink_mock, unlink_mock,
                         islink_mock, lexists_mock,
                         realmock):
    lexists_mock.return_value = True
    realmock.return_value = "/old_to_link_path"
    islink_mock.return_value = True
    with Environment('/') as env:
      Link("/some_path",
           to="/a/b/link_to_path"
      )
      
    unlink_mock.assert_called_with("/some_path")
    symlink_mock.assert_called_with("/a/b/link_to_path", "/some_path")
    
  @patch.object(os.path, "realpath")
  @patch.object(os.path, "lexists")
  @patch.object(os.path, "islink")
  def test_action_create_failed_due_to_file_exists(self, islink_mock,
                         lexists_mock, realmock):
    lexists_mock.return_value = True
    realmock.return_value = "/old_to_link_path"
    islink_mock.return_value = False
    with Environment('/') as env:
      try:
        Link("/some_path",
             to="/a/b/link_to_path"
        )
        
        self.fail("Must fail when directory or file with name /some_path exist")
      except Fail as e:
        self.assertEqual("LinkProvider[Link['/some_path']] trying to create a symlink with the same name as an existing file or directory",
                       str(e))
        
  @patch.object(os.path, "lexists")
  @patch.object(os, "symlink")
  def test_action_create_symlink_clean_create(self, symlink_mock, lexists_mock):
    lexists_mock.return_value = False
    
    with Environment('/') as env:
      Link("/some_path",
           to="/a/b/link_to_path"
      )
      
    symlink_mock.assert_called_with("/a/b/link_to_path", "/some_path")
    
  @patch.object(os.path, "isdir")
  @patch.object(os.path, "exists")  
  @patch.object(os.path, "lexists")
  @patch.object(os, "link")
  def test_action_create_hardlink_clean_create(self, link_mock, lexists_mock,
                                        exists_mock, isdir_mock):
    lexists_mock.return_value = False
    exists_mock.return_value = True
    isdir_mock.return_value = False
    
    with Environment('/') as env:
      Link("/some_path",
           hard=True,
           to="/a/b/link_to_path"
      )
      
    link_mock.assert_called_with("/a/b/link_to_path", "/some_path")
    
  @patch.object(os.path, "exists")  
  @patch.object(os.path, "lexists")
  def test_action_create_hardlink_target_doesnt_exist(self, lexists_mock,
                                        exists_mock):
    lexists_mock.return_value = False
    exists_mock.return_value = False
    
    with Environment('/') as env:
      try:
        Link("/some_path",
             hard=True,
             to="/a/b/link_to_path"
        )  
        self.fail("Must fail when target directory do doenst exist")
      except Fail as e:
        self.assertEqual("Failed to apply Link['/some_path'], linking to nonexistent location /a/b/link_to_path",
                       str(e))
        
  @patch.object(os.path, "isdir") 
  @patch.object(os.path, "exists")  
  @patch.object(os.path, "lexists")
  def test_action_create_hardlink_target_is_dir(self, lexists_mock,
                                        exists_mock, isdir_mock):
    lexists_mock.return_value = False
    exists_mock.return_value = True
    isdir_mock = True
    
    with Environment('/') as env:
      try:
        Link("/some_path",
             hard=True,
             to="/a/b/link_to_path"
        )  
        self.fail("Must fail when hardlinking to directory")
      except Fail as e:
        self.assertEqual("Failed to apply Link['/some_path'], cannot create hard link to a directory (/a/b/link_to_path)",
                       str(e)) 
        
  @patch.object(os, "unlink")
  @patch.object(os.path, "exists")
  def test_action_delete(self, exists_mock, unlink_mock):     
    exists_mock.return_value = True
    
    with Environment('/') as env:
      Link("/some_path",
           action="delete"
      )    
    unlink_mock.assert_called_with("/some_path")
      
  
