// Test basic [*].* - all fields of all array elements

include "console.iol"

main {
    data[0].x = 10;
    data[0].y = 20;
    data[1].x = 30;
    data[1].y = 40;

    res << paths data[*].* where true;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
