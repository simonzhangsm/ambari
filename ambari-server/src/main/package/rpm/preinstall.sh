#!/bin/bash +xv

case "$1" in
    install)
        # do some magic
        if [ -d "/etc/ambari-server/conf.save" ]
		then
		    sudo mv /etc/ambari-server/conf.save /etc/ambari-server/conf_$(date '+%d_%m_%y_%H_%M').save
		fi
        ;;

    upgrade|abort-upgrade)
    	if [ -d "/etc/ambari-server/conf.upgrade" ]
		then
		    sudo mv /etc/ambari-server/conf.upgrade /etc/ambari-server/conf_$(date '+%d_%m_%y_%H_%M').upgrade
		fi
        ;;

    *)
        echo "preinst called with unknown argument \`$1'" >&2
        exit 0
        ;;
esac

exit 0
