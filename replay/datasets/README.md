# Datasets

## `pmdata-p01.json`: PMData participant p01 (public, CC BY 4.0)

Five months (Nov 2019 – Mar 2020) of one participant's real Fitbit Versa 2 data:
- 150 nights with sleep stages
- 152 days of resting heart rate
- hourly step counts
- 190 workouts
- 137 of the nights carry the participant's **own 1–5 sleep-quality rating**, from the PMSys wellness log (≤ 2 = bad night).

HomeOS loads it as a separate household member `p01`, clearly labelled as a public dataset and never present in the home. That gives the Scientist's graph algorithms months of real, labelled history. The team's own Gabit ring had almost no history before the event: the ring → Health Connect → HomeOS Bridge pipeline is verified end to end, but there was little to sync.

```bash
cd backend && npx tsx scripts/import-pmdata.ts --batch ../replay/datasets/pmdata-p01.json --alias p01
```

**Source and licence:** V. Thambawita, S. A. Hicks, H. Borgli, H. K. Stensland, D. Jha, M. K. Svensen, S.-A. Pettersen, D. Johansen, H. D. Johansen, S. D. Pettersen, S. Nordvang, S. Pedersen, A. Gjerdrum, T.-M. Grønli, P. M. Fredriksen, R. Eg, K. Hansen, S. Fagernes, C. Claudi, A. Biørn-Hansen, D. T. Dang Nguyen, T. Kupka, H.-P. Hammer, R. Jain, M. A. Riegler, P. Halvorsen. *PMData: A Sports Logging Dataset.* ACM MMSys '20, pp. 231–236. https://datasets.simula.no/pmdata/. Licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

**Changes made:**
- converted to the HomeOS Health Connect batch format (`backend/scripts/import-pmdata.ts`);
- per-minute steps summed per hour;
- overlapping monthly exports de-duplicated.

No values were altered or synthesised.
