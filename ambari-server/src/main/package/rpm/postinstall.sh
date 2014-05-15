#!/bin/bash +xv

case "$1" in
    install)
		echo "preinst should be called ..."
		exit 0
		;;
	configure)
	
		if [ -f "/var/lib/ambari-server/install-helper.sh" ]; then
			/var/lib/ambari-server/install-helper.sh install
		fi
		if [ -e "/etc/init.d/ambari-server" ];
		then # Check is needed for upgrade # Remove link created by previous package version
		    sudo rm -rf /etc/init.d/ambari-server
		fi
		
		sudo ln -s /usr/sbin/ambari-server /etc/init.d/ambari-server
		
		which chkconfig > /dev/null 2>&1 #for redhat
	    if [ $? -eq 0 ]; then
	    	sudo chkconfig --add ambari-server
	    else
	    	which update-rc.d > /dev/null 2>&1 #ubuntu with update-rc.d
	    	if [ $? -eq 0 ]; then 
	    		sudo update-rc.d ambari-server defaults
	    	else
	    		which sysv-rc-conf > /dev/null 2>&1 #ubuntu with sysv-rc-conf
	    		if [ $? -eq 0 ]; then 
	    			sudo sysv-rc-conf ambari-server on
	    		fi
	    	fi
	    fi 
		;;
	*)
        echo "postinst called with unknown argument \`$1'" >&2
        exit 0
        ;;
esac

exit 0