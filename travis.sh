#!/bin/bash
# Abort on Error
set -e

export PING_SLEEP=30s
export WORKDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
export BUILD_OUTPUT=$WORKDIR/build.out

touch $BUILD_OUTPUT

dump_output() {
   echo Tailing the last 5000 lines of output:
   tail -5000 $BUILD_OUTPUT  
}
error_handler() {
  echo ERROR: An error was encountered with the build.
  dump_output
  exit 1
}
# If an error occurs, run our error handler to output a tail of the build
trap 'error_handler' ERR

# Set up a repeating loop to send some output to Travis.

bash -c "while true; do echo \$(date) - building ...; $(dump_output); sleep $PING_SLEEP; done" &
PING_LOOP_PID=$!

# Run the build
$CD_COMMAND
$TEST_COMMAND >> $BUILD_OUTPUT 2>&1

# The build finished without returning an error so dump a tail of the output
dump_output
