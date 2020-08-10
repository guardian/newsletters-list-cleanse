# newsletter-list-cleanse

A monthly job to clean newsletter mailing lists of lapsed subscribers.

## Structure ##

The job is split into three lambdas that are joined together using SQS.

* **getCutOffDates**: Get the cut off dates for all newsletters.
* **getCleanseList**: Use the cut off dates to get a list of users to be removed from this distribution list. 
This is done on a per-newsletter basis.
* **updateBrazeUsers**: Update Braze using the cleanse list.


