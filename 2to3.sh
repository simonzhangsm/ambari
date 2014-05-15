#!/bin/bash

2to3 -w `find $1 -name "*.py"`

2to3 -w `grep --exclude=$0 "/usr/bin/env python" -rl $1`
sed -i ".sedbak" "s/import\ string/#import\ string/g" `grep "import string" -rl .`
sed -i ".sedbak" "s/\/usr\/bin\/env\ python2.6/\/usr\/bin\/env\ python/g" `grep "/usr/bin/env python" -rl .`
sed -i ".sedbak" "s/\/usr\/bin\/python/\/usr\/bin\/env\ python/g" `grep "/usr/bin/python" -rl .`

[ $2 == "clean" ] && rm -rf `find $1 -name "*.bak" && rm -rf `find $1 -name "*.sedbak"`
