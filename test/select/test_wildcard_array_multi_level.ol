// Test multi-level wildcard array: data.*.*[*]
// Should select all array elements from grandchildren

include "console.iol"

main {
    data.x.alpha[0] = 5;
    data.x.alpha[1] = 10;
    data.x.beta[0] = 15;
    data.y.gamma[0] = 20;
    data.y.gamma[1] = 25;

    res << paths data.*.*[*] where $ > 12;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
