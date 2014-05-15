#!/usr/bin/env bash +xv

case "$1" in
    install)
		if [ -d "/etc/ambari-agent/conf.save" ]; then
		    sudo mv /etc/ambari-agent/conf.save /etc/ambari-agent/conf_$(date '+%d_%m_%y_%H_%M').save
		fi
		if [ -f "/etc/ambari-agent/conf/ambari-agent.ini" ]; then
			sudo mv -f /etc/ambari-agent/conf/ambari-agent.ini /etc/ambari-agent/conf/ambari-agent.ini.old
		fi
		;;
	upgrade|abort-upgrade)
		if [ -d "/etc/ambari-agent/conf.upgrade" ]; then
		    sudo mv /etc/ambari-agent/conf.upgrade /etc/ambari-agent/conf_$(date '+%d_%m_%y_%H_%M').upgrade
		fi
		if [ -f "/etc/ambari-agent/conf/ambari-agent.ini.upgrade" ]; then
			sudo mv -f /etc/ambari-agent/conf/ambari-agent.ini.upgrade /etc/ambari-agent/conf/ambari-agent.ini.$(date '+%d_%m_%y_%H_%M').upgrade
		fi
        ;;
	*)
		echo "preinst called with unknown argument \`$1'" >&2
        exit 0
        ;;

exit 0
