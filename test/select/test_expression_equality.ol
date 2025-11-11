include "console.iol"

main {
    root.x = 10;
    root.y = 20;
    root.z = 10;

    // Use SELECT as expression with deep copy operator
    result2 << select "$.*" into results1 from root where ". == 10";

    // Print results1 (side effect from INTO)
    i = 0;
    while( i < #results1 ) {
        println@Console(results1[i])();
        i++
    };

    // Print separator
    println@Console("---")();

    // Print result2.result (return value from expression)
    i = 0;
    while( i < #result2.result ) {
        println@Console(result2.result[i])();
        i++
    }
}
