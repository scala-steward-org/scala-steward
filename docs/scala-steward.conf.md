# .scala-steward.conf

## pullRequests

### frequency

Allows to control how often or when Scala Steward is allowed to create pull requests.

Possible values:
 * `"@asap"`:
   Bla bla bla
   
 * `"@daily"`:
   Bla bla bla
 
 * `"@weekly"`:
 
 * `"@monthly"`:
  
 * `"<CRON expression>"`:


Default: `"@asap"`

```properties
pullRequests.frequency = "@weekly"
```

## updates

### limit

Default: `null`

## updatesPullRequests

Possible values:

Default: `"on-conflicts"`
