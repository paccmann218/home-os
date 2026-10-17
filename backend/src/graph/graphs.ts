/**
 * Role-scoped access to the two kinds of graph:
 *   bodyRead / bodyWrite  -> body:<alias>, as that person's ACL user (cannot touch anyone else)
 *   homeRead              -> home:<id>, as home_reader (GRAPH.RO_QUERY only)
 *   homeWrite             -> home:<id>, as the write API (default user)
 */
import { q, agentUser, HOME_READER, type Params, type Row } from "./db.js";
import { bodyGraph, homeGraph } from "../config.js";

export const bodyRead = (alias: string, name: string, params: Params = {}): Promise<Row[]> =>
  q(bodyGraph(alias), name, params, { as: agentUser(alias), readOnly: true });

export const bodyWrite = (alias: string, name: string, params: Params = {}): Promise<Row[]> =>
  q(bodyGraph(alias), name, params, { as: agentUser(alias) });

/** Algorithms (SPpaths, pageRank, ...) can be slow on big histories: give them more time. */
export const bodyAlgo = (alias: string, name: string, params: Params = {}, write = false): Promise<Row[]> =>
  q(bodyGraph(alias), name, params, { as: agentUser(alias), readOnly: !write, timeoutMs: 30000 });

export const homeRead = (name: string, params: Params = {}): Promise<Row[]> =>
  q(homeGraph(), name, params, { as: HOME_READER, readOnly: true });

export const homeWrite = (name: string, params: Params = {}): Promise<Row[]> => q(homeGraph(), name, params);
