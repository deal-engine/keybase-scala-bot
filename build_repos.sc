import $ivy.`io.get-coursier:interface:0.0.21`

// Dont use sonatype's maven-central as it timeouts in travis.
interp.repositories() =
  List(coursierapi.MavenRepository.of("https://jcenter.bintray.com"))
