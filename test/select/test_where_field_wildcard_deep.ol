// Test deep field wildcard: $.*.*.value

include "console.iol"

main {
    root.x.alpha.value = 100;
    root.x.beta.value = 50;
    root.y.gamma.value = 200;
    root.y.delta.value = 75;
    root.z.epsilon.value = 150;

    // Find root where any deep path has value > 175
    res << paths root where $.*.*.value > 175;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
