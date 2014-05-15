test:
	mvn test -Dmaven.test.failure.ignore=true

mvnversion:
	mvn --version

newversion:
	mvn versions:set -DnewVersion=1.6.x
	
rpm:
	mvn -X -B clean install package rpm:rpm -DskipTests #-Dpython.ver="python >= 3.3"

python: 
	./compile .

clean: 
	mvn clean

clear: 
	find . -name target | xargs rm -rf
	find . -name __pycache__ | xargs rm -rf
	find . -name *.bak | xargs rm
	find . -name *.pyc | xargs rm
	find . -name *.pyo | xargs rm

all: newversion rpm test

install:
	mvn install

.PHONY: rpm 
