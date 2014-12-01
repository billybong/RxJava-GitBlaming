import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.http.GET;
import retrofit.http.Headers;
import retrofit.http.Path;
import rx.Observable;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public class App {
    private final GitHub gitHub;
    private final FuckOfService fuckOfService;

    public App(String user, String password) {
        HttpBasicInterceptor basicInterceptor = new HttpBasicInterceptor(user, password);

        gitHub = new RestAdapter.Builder()
                .setLogLevel(RestAdapter.LogLevel.NONE)
                .setRequestInterceptor(basicInterceptor)
                .setEndpoint("https://api.github.com/")
                .build().create(GitHub.class);

        fuckOfService = new RestAdapter.Builder()
                .setLogLevel(RestAdapter.LogLevel.NONE)
                .setRequestInterceptor(basicInterceptor)
                .setEndpoint("http://foaas.herokuapp.com")
                .build().create(FuckOfService.class);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Options options = new Options();
        CmdLineParser parser = new CmdLineParser(options);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            System.err.println("rxjava [options...] arguments...");
            parser.printUsage(System.err);
            return;
        }

        new App(options.gitHubUser, options.gitHubPassword).tellComittersToFuckOff(options.repoOwner, options.repo, options.gitHubUser).subscribe(
                fo -> System.out.println(fo.message),
                System.out::println);
    }

    static class Options {
        @Option(name ="-gu", usage="your github.com username", required = true)
        String gitHubUser;
        @Option(name ="-gp", usage="your github.com password", required = true)
        String gitHubPassword;
        @Option(name ="-ro", usage="owner of the repo you dislike", required = true)
        String repoOwner;
        @Option(name ="-r", usage="github repo of project that needs to feel your wrath", required = true)
        String repo;
    }

    public Observable<FuckOfResponse> tellComittersToFuckOff(String githubUser, String githubRepo, String fromWho){
        return gitHub.contributors(githubUser, githubRepo)
                .flatMap((contributors) -> {
                            contributors.sort((c1, c2) -> c1.contributions  > c2.contributions ? 1:-1);
                            return Observable.from(contributors);
                        })
                        .filter(contributor -> contributor.contributions > 10)
                        .flatMap(contributor -> gitHub.user(contributor.login))
                        .flatMap(user -> fuckOfService.fuckOff(Optional.ofNullable(user.name).orElse("Unknown"), fromWho)
                );
    }

    interface FuckOfService {
        @Headers("Accept: application/json")
        @GET("/off/{name}/{from}")
        Observable<FuckOfResponse> fuckOff(@Path("name") String name, @Path("from") String from);
    }

    interface GitHub {
        @GET("/repos/{owner}/{repo}/contributors")
        Observable<List<Contributor>> contributors(@Path("owner") String owner, @Path("repo") String repo);

        @GET("/users/{username}")
        Observable<GitUser> user(@Path("username") String user);
    }

    static class Contributor {
        private final String login;
        private final long contributions;

        Contributor(String login, long contributions) {
            this.login = login;
            this.contributions = contributions;
        }
    }

    static class GitUser {
        private final String name;

        GitUser(String name) {
            this.name = name;
        }
    }

    static class FuckOfResponse {
        private final String message;

        FuckOfResponse(String message) {
            this.message = message;
        }
    }

    static class HttpBasicInterceptor implements RequestInterceptor {

        private final String username;
        private final String password;

        HttpBasicInterceptor(String username, String password) {
            this.username = username;
            this.password = password;
        }

        @Override
        public void intercept(RequestFacade request) {
            request.addHeader("Authorization", encodeCredentialsForBasicAuthorization());
        }

        private String encodeCredentialsForBasicAuthorization() {
            final String userAndPassword = username + ":" + password;
            return "Basic " + Base64.getEncoder().encodeToString(userAndPassword.getBytes());
        }
    }
}
