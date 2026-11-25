# Records: only beaten PRs, reps at the heaviest weight

2026-11-25. Approved by the owner in chat on the same day.

## Problem

A 25 kg leg extension set showed as a "First" record although the owner had done 80 kg × 9 on the same exercise. Records were already per exercise (exercise ID and load convention, across routine and empty workouts alike). The "First" came from repetition records being kept separately for every load: the first set at a load never used before had nothing to beat and was shown as a "First" record. The first performance of an exercise was shown the same way.

## Behavior

A record is shown only when it beats an earlier value of the same exercise and load convention, from any earlier finished workout:

- **Weight PR:** the workout's heaviest working set is heavier than the exercise's previous heaviest. Unchanged, except that the first performance (no previous heaviest) is no longer a record.
- **Reps PR (owner's choice: only at the heaviest weight):** a working set at the exercise's heaviest load, counting this workout, has more reps than ever before at that load. Lighter loads never give a reps record. For the Assistance convention the hardest setting, the least assistance, takes the place of the heaviest load. A workout that sets a new heaviest load gives a weight PR there, not a reps PR.
- **No first records:** nothing is shown for the first time an exercise is done or the first set at a new load. Those sets still count as history for later comparisons.
- **Current records** list only records that beat something: the current weight record when it was set by beating an earlier one, and the current reps record at the heaviest load when it was set by beating an earlier value at that load.

Removed from the app: the Records tab's "First records" filter, "First" badge and "only first records" empty state; the "First records" sections of the workout summary and the saved workout; the history list's first-record count; first records on share cards and in the spoken record text. The PR count on the summary, history and share card counts only beaten records. The Records empty title becomes "Your records show up here".

Unchanged: saved data (display only, no schema change), Follow the record routine targets, the active workout's record badges and toast (already ignored first records), and "First time" in the Previous column (not a record).

## Tests

Domain tests for: the first performance gives no record; a first set at a new lighter load gives no record, whether the workout came from a routine or was empty; more reps at the heaviest load is a reps PR; more reps at a lighter load is not; a new heaviest load gives only a weight PR; assistance uses the least assistance; current records omit unbeaten records. Screen tests updated for the removed chip, badge and sections.

## Revision (2026-11-25, later the same day): the first session counts

Testing the Preview, the owner finished an exercise for the first time and saw no PR. The owner chose to count an exercise's first session as a normal PR. This replaces "No first records" above; nothing else changes.

- **First session:** an exercise's first session (per exercise ID and load convention) is a PR, shown with the normal "PR" badge and no "was …", counted in the PR totals, Records, history and share cards. Exactly one record per exercise: the weight PR (its heaviest working set), or, when the session has no load record to give (no added load, or assisted sets), a reps PR at its hardest load.
- **Later sessions:** unchanged. A weight PR beats the previous heaviest; a reps PR needs more reps than ever at the heaviest load (least assistance). The first set at a new lighter load is still never a record.
- **Current records:** the exercise's best load, and the reps record at the hardest load when it was set by beating an earlier value or by a first session as above.
- **Live workout:** per-set badges and the PR toast stay for records that beat an earlier value (D18), so a first workout does not celebrate every exercise.
- **Texts:** there is still no "First" label anywhere. The rules sheet's load rule is "Load: your heaviest working set." again, and the Records empty state body is again "Complete working sets to set load and rep records."
