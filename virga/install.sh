#!/bin/bash

dir=`dirname $0`
cd $dir/..
pypath=`pwd`
if [ "X${PYTHONPATH}" == "X" ]; then
    export PYTHONPATH=$pypath
else
    export PYTHONPATH=$pypath:${PYTHONPATH}
fi
# so that we pick up the ini file
export VIRGA_HOME=$pypath

exec python ./pyvirga/request.py "${@}"
