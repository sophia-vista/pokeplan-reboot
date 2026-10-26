// Runs the compiled reward engine under Node, the way the server will.
//
//   ./gradlew :pokemon-core:jsProductionLibraryCompileSync
//   node tools/server/smoke.mjs pokemon-core/build/compileSync/js/main/productionLibrary/kotlin
//
// Exits non-zero if anything looks wrong.
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { pathToFileURL } from "node:url";

const source = resolve(process.argv[2] ?? "pokemon-core/build/compileSync/js/main/productionLibrary/kotlin");
const lib = await import(pathToFileURL(join(source, "pokeplan-pokemon-core.mjs")).href);
const api = lib.RewardApi.getInstance();

const check = (ok, message) => {
  if (!ok) {
    console.error("FAIL:", message);
    process.exit(1);
  }
  console.log("ok:", message);
};

const state = api.emptyState();
const effort = JSON.stringify({ minutes: 25, deepFocus: false, region: "KANTO", newPokemonId: "egg-1", nowMillis: 1700000000000 });
const out = JSON.parse(api.effort(state, effort, 42));
const hatched = out.rewards.find((r) => r.kind === "hatched");
check(hatched && hatched.pokemon.id === "egg-1", "an effort event hatches an egg");
check(out.state.pokemon.length === 1 && out.state.coins === 5, "the state gains the Pokémon and 5 coins");
check(out.state.trainerPoints === 5, "25 minutes of effort earn 5 trainer points");
check(out.state.caught.length === 1, "the species is recorded as caught");

const again = api.effort(state, effort, 42);
check(again === api.effort(state, effort, 42), "the same seed gives the same outcome");
check(again !== api.effort(state, effort, 43), "a different seed can give a different outcome");

const work = JSON.stringify({ tier: 4, region: "KANTO", newPokemonId: "egg-2", nowMillis: 1700000000000 });
const done = JSON.parse(api.achievement(JSON.stringify(out.state), work, 7));
check(done.rewards.some((r) => r.kind === "candies"), "finished work pays candies");
check(done.state.trainerPoints === 5 + 8, "a tier 4 task is worth 8 trainer points");

let refused = false;
try {
  api.effort(state, "{}", 1);
} catch (e) {
  refused = true;
}
check(refused, "a malformed event is refused");

// The same fixed input must give exactly the JSON the JVM gave (tools/server/golden.json).
const golden = JSON.parse(readFileSync(join(dirname(fileURLToPath(import.meta.url)), "golden.json"), "utf8"));
check(api.effort(golden.state, golden.effortEvent, golden.effortSeed) === golden.effortOutcome, "JS effort outcome is identical to the JVM's");
check(api.achievement(golden.state, golden.achievementEvent, golden.achievementSeed) === golden.achievementOutcome, "JS achievement outcome is identical to the JVM's");
check(api.care(golden.state, golden.careEvent, golden.careSeed) === golden.careOutcome, "JS care outcome is identical to the JVM's");
check(api.rulesVersion() === golden.rulesVersion, `the rules version is ${golden.rulesVersion}, as in the golden file`);
console.log("all good");
