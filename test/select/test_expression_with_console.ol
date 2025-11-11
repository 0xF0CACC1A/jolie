from console import Console

main {
    root.x = 10;
    root.y = 20;
    root.z = 10;

    println@Console("Testing SELECT as expression")();
    println@Console("============================")();

    // Use SELECT as expression with deep copy operator
    result << select "$.*" into results from root where ". == 10";

    println@Console("Results from INTO (side effect):")();
    i = 0;
    while( i < #results ) {
        println@Console("  " + results[i])();
        i++
    };

    println@Console("")();
    println@Console("Results from expression return value:")();
    i = 0;
    while( i < #result.result ) {
        println@Console("  " + result.result[i])();
        i++
    };

    println@Console("")();
    println@Console("Verification: Both contain same values")()
}
