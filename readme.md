# Merge script

## File access

The scripts are executed with the correct node-directory set as the executing directory.
This means regular file.list etc scripts can be used. We want to give maximum freedom in how you merge the end result.
We also want minimal overhead. Loading an entire web application into memory to modify a single file, is wasteful. Especially if we also load the previous version of all those files.

## Parameters

The merge script should be rerunnable, it can be run multiple times on a single branch. The reason for this is, in the first run there will not be any parameters. The script can however create new parameters. These parameters can be prompted to the user to modify.

Once modified, the user can start a new build cycle.