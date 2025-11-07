// Test: Direct Field with Single Dot
// Expected: projects[0] (status==active AND technologies==Python)

main {
    projects[0].status = "active";
    projects[0].technologies = "Python";

    projects[1].status = "active";
    projects[1].technologies = "Java";

    projects[2].status = "inactive";
    projects[2].technologies = "Python";

    projects[3].status = "active";

    select "$[*]" into results from projects where ".status == active && .technologies == Python";

    i = 0;
    while( i < #results ) {
        print results[i];
        i++
    }
}
