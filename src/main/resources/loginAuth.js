/*global AJS: false*/
( function( $ ) {
    var formIsLoaded = false,

        loadCorpLogin = function( $loginForm ) {
            var query = location.search.substr( 1 ),

                redirectMessage = "Please wait while we redirect you to your company log in page",

                hideNativeLoginForm = true,

                // keep track of whether we allow Forced SSO Login, regardless of
                // user prefs.  (this is needed to prevent loops if SSO login fails)
                forcedSsoLoginAllowed = true;

            // TODO:
            //  Keycloak-initiated login:
            //    - If we're not logged-in to JIRA, check if user is logged in (via custom iframe or keycloak js adapter)
            //    - If user is logged in SSO but not JIRA, redirect to SSO so that JIRA knows we're logged in
            //
            //  Keycloak-initiated logout (maybe?):
            //    - If we're logged-in to JIRA and we're a SSO user, check (at intervals) if user is still logged-in to SSO
            //    - If not, logout of JIRA (or have a "Session timed-out, relogin _here_" popup message thing
            //
            //  JIRA-initiated logout:
            //    - If we're an SSO user, logout of Keycloak, then logout of JIRA
            $.ajax( {
                url: AJS.contextPath() + "/plugins/servlet/saml/getajaxconfig?param=idpRequired",
                type: "GET",
                error: function() {},
                success: function( response ) {
                    if ( response === "true" && forcedSsoLoginAllowed ) {
                        $( "<p>" + redirectMessage + "</p>" ).insertBefore( $loginForm );

                        window.location.href = AJS.contextPath() + "/plugins/servlet/saml/auth";
                    }
                }
            } );

            if ( formIsLoaded === true ) {
                return;
            }

            if ( location.search === "?logout=true" ) {
                $.ajax( {
                    url: AJS.contextPath() + "/plugins/servlet/saml/getajaxconfig?param=logoutUrl",
                    type: "GET",
                    error: function() {},
                    success: function( response ) {
                        if ( response !== "" ) {
                            $( "<p>" + redirectMessage + "</p>" ).insertBefore( $loginForm );

                            window.location.href = response;
                        }
                    }
                } );

                return;
            }

            query.split( "&" ).forEach( function( part ) {
                var item = part.split( "=" );

                if ( item.length === 2 && item[ 0 ] === "nosaml" ) {
                    hideNativeLoginForm = false;
                }

                if ( item.length === 2 && item[ 0 ] === "samlerror" ) {
                    var errorKeys = {},
                        message = "";

                    errorKeys[ "general" ] = "General SAML configuration error";
                    errorKeys[ "user_not_found" ] = "User was not found";
                    errorKeys[ "plugin_exception" ] = "SAML plugin internal error";

                    $loginForm.show();

                    message = "<div class='aui-message closeable error'>" + errorKeys[ item[ 1 ] ] + "</div>";
                    $( message ).insertBefore( $loginForm );

                    // there's a SAML error, so don't keep trying to login
                    forcedSsoLoginAllowed = false;
                }
            } );

            if ( hideNativeLoginForm === true ) {
                $loginForm.hide();
                $( "<div class='field-group' style='text-align: center;'><a class='aui-button aui-style aui-button-primary' href='" + AJS.contextPath() + "/plugins/servlet/saml/auth' style='align:center;'>Use Corporate login</a></div>" ).insertBefore( $loginForm );
                formIsLoaded = true;
            }
        };

    // Login form on the /login.jsp page
    $( document ).ready( function() {
        var $loginForm = $( "#login-form" ),
            isWebSudo = ( $loginForm.attr( "action" ) === "/secure/admin/WebSudoAuthenticate.jspa" );

        if ( $loginForm.length === 1 && !isWebSudo ) {
            loadCorpLogin( $loginForm );
        }
    } );

    // Login form on the dashboard
    //
    // document.ready (sometimes) doesn't work here since
    // the dashboard is built with JS
    $( document ).on( "gadgets-loaded", function( e ) {
        var $loginForm = $( "#loginform" );

        if ( $loginForm.length === 1 ) {
            loadCorpLogin( $loginForm );
        }
    } );
}( AJS.$ ) );
