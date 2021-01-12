# newsletter-list-cleanse

A monthly job to clean newsletter mailing lists of lapsed subscribers.

The job is a single lambda that is executing three steps:
 - In the datalake, Identify the "cut-off date" for each newsletter. It determines how long a user has to be inactive to be considered as "lapsed"
 - Fetch the list of users that have lapsed from the datalake.
 - Update these users in braze such that they aren't subscribed anymore.  

## Run locally

You'll need a file in `~/.gu/newsletter-list-cleanse.conf` that contains all the keys defined in SSM under `/identity/newsletter-list-cleanse/CODE/`

Ensure the `dryRun` config value is either undefined or set to `true` before running the program. If you need to test the braze update, ensure you're pointing to the test Braze environment.

```sbtshell
test:run
```

## Test locally

```sbtshell
test
```

