// Test: Nested Path Selection
// Expected output: data.items.x, data.items.z

main {
    data.items.x = 10;
    data.items.y = 20;
    data.items.z = 10;

    select "$.*" into results from data.items where ". == 10";

    i = 0;
    while( i < #results ) {
        print results[i];
        i++
    }
}
