// Test: Wildcard Selection with WHERE
// Expected output: root.x, root.z

main {
    root.x = 10;
    root.y = 20;
    root.z = 10;

    select "$.*" into results from root where ". == 10";

    i = 0;
    while( i < #results ) {
        print results[i];
        i++
    }
}
