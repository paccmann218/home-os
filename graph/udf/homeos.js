// HomeOS UDF library, loaded into FalkorDB with GRAPH.UDF LOAD (see backend/src/graph/setup.ts).
// Called from Cypher as HomeOS.<fn>(...). UDFs are read-only: they compute values,
// and the surrounding Cypher query does the SET, so each learn step stays one atomic query.

// One step of Welford's online mean/variance: folds a new outcome x into (n, mean, m2).
function welford(n, mean, m2, x) {
  const n1 = n + 1;
  const d = x - mean;
  const mean1 = mean + d / n1;
  return { n: n1, mean: mean1, m2: m2 + d * (x - mean1) };
}

// Thompson-sampling draw for an intervention's effect: Normal(mean, sd/sqrt(n)).
// Few observations -> wide draw -> the planner keeps exploring.
function thompson(n, mean, m2) {
  const k = Math.max(n, 1);
  const variance = k > 1 ? m2 / (k - 1) : 1;
  const sd = Math.sqrt(Math.max(variance, 0.05)) / Math.sqrt(k);
  const u = 1 - Math.random();
  const v = Math.random();
  return mean + sd * Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
}

// How far a reading sits from a person's baseline, in standard deviations.
function zscore(x, mean, sd) {
  if (sd === null || sd === undefined || sd <= 0) return 0;
  return (x - mean) / sd;
}

// Discretise a z-score or raw value into a context band level.
function level(z, lowCut, highCut) {
  if (z < lowCut) return "low";
  if (z > highCut) return "high";
  return "normal";
}

// Intersect comfort ranges [{min,max}] -> {min,max,feasible}. Missing bounds are ignored.
function intersectRanges(ranges) {
  let lo = -1e9;
  let hi = 1e9;
  for (const r of ranges) {
    if (r === null || r === undefined) continue;
    if (r.min !== null && r.min !== undefined && r.min > lo) lo = r.min;
    if (r.max !== null && r.max !== undefined && r.max < hi) hi = r.max;
  }
  return {
    min: lo === -1e9 ? null : lo,
    max: hi === 1e9 ? null : hi,
    feasible: lo <= hi,
  };
}

// Resolve possibly-conflicting ranges [{min,max,priority,id}] by priority: higher-priority ranges
// are applied first; a lower-priority range that would make the band empty is overridden (its id is
// reported) instead of producing an impossible setting. E.g. a sick person's 26 °C floor (priority 3)
// beats a post-workout 25 °C ceiling (priority 1).
function resolveRanges(ranges) {
  const sorted = ranges
    .filter((r) => r !== null && r !== undefined)
    .sort((a, b) => (b.priority || 0) - (a.priority || 0));
  let lo = -1e9;
  let hi = 1e9;
  const kept = [];
  const overridden = [];
  for (const r of sorted) {
    const nlo = r.min !== null && r.min !== undefined ? Math.max(lo, r.min) : lo;
    const nhi = r.max !== null && r.max !== undefined ? Math.min(hi, r.max) : hi;
    if (nlo <= nhi) {
      lo = nlo;
      hi = nhi;
      if (r.id) kept.push(r.id);
    } else if (r.id) {
      overridden.push(r.id);
    }
  }
  return {
    min: lo === -1e9 ? null : lo,
    max: hi === 1e9 ? null : hi,
    feasible: overridden.length === 0,
    kept: kept,
    overridden: overridden,
  };
}

// Clamp a requested setpoint into [min, max] (null bounds = open).
function clamp(x, min, max) {
  let v = x;
  if (min !== null && min !== undefined && v < min) v = min;
  if (max !== null && max !== undefined && v > max) v = max;
  return v;
}

// Likelihood of a cause chain from the summed -ln(p) edge weights.
function likelihood(pathWeight) {
  return Math.exp(-pathWeight);
}

falkor.register("welford", welford);
falkor.register("thompson", thompson);
falkor.register("zscore", zscore);
falkor.register("level", level);
falkor.register("intersectRanges", intersectRanges);
falkor.register("resolveRanges", resolveRanges);
falkor.register("clamp", clamp);
falkor.register("likelihood", likelihood);
