#!/usr/bin/env python

import sys
from os.path import dirname, isfile, isdir
import compileall

def main():
    for i in range(1, len(sys.argv)):
        if isdir(sys.argv[i]):
            compileall.compile_dir(sys.argv[i], maxlevels=20, optimize=1, force=1)
        elif isfile(sys.argv[i]):
            compileall.compile_file(sys.argv[i], optimize=1, force=1)

if __name__ == "__main__":
    if len(sys.argv) < 2 :
        print(("Usage: " , sys.argv[0], "directories.\n"))
        sys.exit(1)
    main() 
