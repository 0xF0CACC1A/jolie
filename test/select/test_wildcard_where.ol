// Test: Wildcard Selection with WHERE
// Expected output: root.x, root.z

include "console.iol"

main {
    root.x = 10;
    root.y = 20;
    root.z = 10;

    paths "$.*" into results from root where ". == 10";

    i = 0;
    while( i < #results ) {
        println@Console(results[i])();
        i++
    }
}
