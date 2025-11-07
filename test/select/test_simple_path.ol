// Test: Simple Path Navigation with WHERE
// Expected output: a.b.c

main {
    a.b.c = 5;

    select "$.b.c" into results from a where ". == 5";

    i = 0;
    while( i < #results ) {
        print results[i];
        i++
    }
}
