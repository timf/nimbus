#!/bin/bash

OWNER_IS_RUNNER_ASSUMPTION="yes"

PYTHON_EXE="/usr/bin/env python"

NIMBUS_HOME_REL="`dirname $0`/.."
NIMBUS_HOME=`cd $NIMBUS_HOME_REL; pwd`

NIMBUS_WEBDIR="$NIMBUS_HOME/web"

if [ ! -d $NIMBUS_WEBDIR ]; then
    echo "Web directory does not exist: $NIMBUS_WEBDIR"
    echo "Is this a real Nimbus installation?"
    exit 1
fi

NIMBUS_CONF="$NIMBUS_HOME/nimbus-setup.conf"
SETUP_ARGS="--conf $NIMBUS_CONF"

NIMBUS_PYLIB="$NIMBUS_WEBDIR/lib/python"
NIMBUS_PYSRC="$NIMBUS_WEBDIR/src/python"

PYTHONPATH="$NIMBUS_PYSRC:$NIMBUS_PYLIB:$PYTHONPATH"
export PYTHONPATH


# ------------------------------------------------------------------------------

if [ "X$OWNER_IS_RUNNER_ASSUMPTION" == "Xyes" ]; then
    # The following script expands the tarballs in lib/
    $NIMBUS_WEBDIR/sbin/install-deps.sh $DEBUG
    if [ $? -ne 0 ]; then
        echo ""
        echo "Dependencies are not set up properly, exiting."
        exit 3
    fi
    
    $PYTHON_EXE $NIMBUS_HOME/sbin/setup.py --basedir $NIMBUS_HOME $SETUP_ARGS
    if [ $? -ne 0 ]; then
        echo ""
        echo "Nimbus is not set up properly, exiting."
        exit 2
    fi
fi

