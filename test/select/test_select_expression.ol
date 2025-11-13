// Test PATHS as expression - validates it works without printing
main {
    root.x = 10;
    root.y = 20;
    root.z = 10;

    // Use PATHS as expression with deep copy operator
    result << paths "$.*" into results from root where ". == 10";

    // Verify results array is populated (2 elements: root.x and root.z)
    if( #results != 2 ) {
        throw( TestFailed, "Expected 2 results from INTO, got " + #results )
    };

    // Verify expression return value has same data
    if( #result.result != 2 ) {
        throw( TestFailed, "Expected 2 results from expression, got " + #result.result )
    };

    // Verify both contain same values
    if( results[0] != result.result[0] ) {
        throw( TestFailed, "First result mismatch" )
    };

    if( results[1] != result.result[1] ) {
        throw( TestFailed, "Second result mismatch" )
    }

    // If we get here, test passed
}
