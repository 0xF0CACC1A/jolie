// Test: Nested Path Selection
// Expected output: data.items.x, data.items.z

include "console.iol"

main {
    data.items.x = 10;
    data.items.y = 20;
    data.items.z = 10;

    select "$.*" into results from data.items where ". == 10";

    i = 0;
    while( i < #results ) {
        println@Console(results[i])();
        i++
    }
}
