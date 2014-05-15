#!/usr/bin/env bash +xv

case "$1" in
    install)
    	#chkconfig --add ambari-agent
    	if [ -f "/var/lib/ambari-agent/install-helper.sh" ]; then
    		/var/lib/ambari-agent/install-helper.sh install
    	fi
		which chkconfig > /dev/null 2>&1 #for redhat
		if [ $? -eq 0 ]; then
			sudo chkconfig --add ambari-agent
		else
			which update-rc.d > /dev/null 2>&1 #ubuntu with update-rc.d
			if [ $? -eq 0 ]; then 
				sudo update-rc.d ambari-agent defaults
			else
				which sysv-rc-conf > /dev/null 2>&1 #ubuntu with sysv-rc-conf
				if [ $? -eq 0 ]; then 
					sudo sysv-rc-conf ambari-agent on
				fi
			fi
		fi 
    	;;
    	
    configure)
		if [ -d "/etc/ambari-agent/conf.save" ]; then
		    sudo cp -f /etc/ambari-agent/conf.save/* /etc/ambari-agent/conf
		    sudo mv /etc/ambari-agent/conf.save /etc/ambari-agent/conf_$(date '+%d_%m_%y_%H_%M').save
		fi
	
		
		BAK=/etc/ambari-agent/conf/ambari-agent.ini.old
		ORIG=/etc/ambari-agent/conf/ambari-agent.ini
		
		if [ -f $BAK ]; then
		  SERV_HOST=`grep -e hostname\s*= $BAK | sed -r -e 's/hostname\s*=//' -e 's/\./\\\./g'`
		  sed -i -r -e "s/(hostname\s*=).*/\1$SERV_HOST/" $ORIG
		  rm $BAK -f
		fi
	*)
        echo "postinst called with unknown argument \`$1'" >&2
        exit 0
        ;;
esac

exit 0
