# Source of Test Data

For a detailed description of the GTFS sample feed and its components, visit the GTFS Schedule Examples page:

[GTFS Schedule Examples](https://gtfs.org/schedule/example-feed/)

The actual data can be downloaded from the following link provided by Google's GTFS specification:

[GTFS Sample Feed 1](https://github.com/google/transit/blob/master/gtfs/spec/en/examples/sample-feed-1.zip?raw=true)

Notes:

- The `transfers.txt` file was added manually.
  ```text
  from_stop_id,to_stop_id,transfer_type,min_transfer_time
  EMSI,STAGECOACH,2,120            // Downhill walk, less time required
  STAGECOACH,EMSI,2,180            // Uphill walk, more time required
  BEATTY_AIRPORT,BEATTY_AIRPORT,0, // Recommended transfer at the airport
  FUR_CREEK_RES,BULLFROG,3,        // Transfer not possible, steep terrain prevents it
  BULLFROG,FUR_CREEK_RES,3,        // Transfer not possible, steep terrain prevents it
  ```
