#!/usr/bin/env bash +xv

# WARNING: This script is performed not only on uninstall, but also
# during package update. See http://www.ibm.com/developerworks/library/l-rpm2/
# for details

case "$1" in
	upgrade|remove)
	    /usr/sbin/ambari-agent stop > /dev/null 2>&1
	    if [ -d "/etc/ambari-agent/conf.save" ]; then
	       sudo mv /etc/ambari-agent/conf.save /etc/ambari-agent/conf_$(date '+%d_%m_%y_%H_%M').save
	    fi
	    sudo mv /etc/ambari-agent/conf /etc/ambari-agent/conf.save
	
		if [ -f "/var/lib/ambari-agent/install-helper.sh" ]; then
			/var/lib/ambari-agent/install-helper.sh remove
		fi
		
		if [ -f "/etc/ambari-agent/conf/ambari-agent.ini.upgrade" ]; then
			sudo mv -f /etc/ambari-agent/conf/ambari-agent.ini.upgrade /etc/ambari-agent/conf/ambari-agent.ini.$(date '+%d_%m_%y_%H_%M').upgrade
		fi
		sudo mv /etc/ambari-agent/conf/ambari-agent.ini /etc/ambari-agent/conf/ambari-agent.ini.upgrade
		
	    # chkconfig --del ambari-agent
	    which chkconfig > /dev/null 2>&1 #for redhat
	    if [ $? -eq 0 ]; then
	    	sudo chkconfig --del ambari-agent
	    else
	    	which update-rc.d > /dev/null 2>&1 #ubuntu with update-rc.d
	    	if [ $? -eq 0 ]; then 
	    		sudo update-rc.d -f ambari-agent remove
	    	else
	    		which sysv-rc-conf > /dev/null 2>&1 #ubuntu with sysv-rc-conf
	    		if [ $? -eq 0 ]; then 
	    			sudo sysv-rc-conf ambari-agent off
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
