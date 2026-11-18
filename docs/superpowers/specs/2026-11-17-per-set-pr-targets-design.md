# PR targets for individual planned sets

Each working set in Follow PR mode uses the best eligible completed set that originated from that exact planned set. Finishing a workout saves its status and refreshes the routine targets in the same Room transaction. The routine editor and new workouts read the saved values.

Starting a routine copies each planned set's stable ID into the workout set. Matching also checks the source routine, exercise variation, and load convention. A position is only for ordering. Replacing an exercise changes which records qualify, even if it keeps the same planned set IDs. Restoring a removed workout set keeps its origin.

The ranking compares canonical kg first and reps at equivalent loads second. The selected set supplies both values. Warm-ups, assisted sets, incomplete sets, and unsaved workouts are ineligible. Manual targets and rest durations are independent of the calculation. An unweighted bodyweight record has a blank load.

Room v7 adds a nullable origin column. Older workouts and ad hoc sets have no origin, so they leave existing targets in place. A new Follow PR set starts blank until it has a record. Backup format v1 carries the origin optionally and loads older files without it. Import uses a link only when the backed-up and resolved planned set, routine, exercise, and convention agree.
