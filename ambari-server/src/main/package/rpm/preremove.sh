#!/bin/bash +xv

case "$1" in
	upgrade|remove)
	    /usr/sbin/ambari-server stop > /dev/null 2>&1
	    if [ -d "/etc/ambari-server/conf.save" ]; then
	        sudo mv /etc/ambari-server/conf.save /etc/ambari-server/conf_$(date '+%d_%m_%y_%H_%M').save
	    fi
	    
	    if [ -e "/etc/init.d/ambari-server" ]; then
	        # Remove link created during install
	        sudo rm -r /etc/init.d/ambari-server
	    fi
	
	    sudo mv /etc/ambari-server/conf /etc/ambari-server/conf.save
	    
	    if [ -f "/var/lib/ambari-server/install-helper.sh" ]; then
	    	/var/lib/ambari-server/install-helper.sh remove
	    fi
	
	    which chkconfig > /dev/null 2>&1 #for redhat
	    if [ $? -eq 0 ]; then
	    	sudo chkconfig --del ambari-server
	    else
	    	which update-rc.d > /dev/null 2>&1 #ubuntu with update-rc.d
	    	if [ $? -eq 0 ]; then 
	    		sudo update-rc.d -f ambari-server remove
	    	else
	    		which sysv-rc-conf > /dev/null 2>&1 #ubuntu with sysv-rc-conf
	    		if [ $? -eq 0 ]; then 
	    			sudo sysv-rc-conf ambari-server off
	    		fi
	    	fi
	    fi
	    ;;
    *)
    	echo "prerm called with unknown argument \`$1'" >&2
        exit 0 
        ;;
esac

exit 0

