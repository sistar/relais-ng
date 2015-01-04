'use strict';

angular.module('myApp', [
    'ngRoute',
    'restangular',
    'ui.bootstrap'
]).
    config(function ($routeProvider, RestangularProvider) {
        $routeProvider.
            when('/', {
                controller: ListCtrl,
                templateUrl: 'views/list.html'
            }).
            when('/edit/:pinName', {
                controller: EditCtrl,
                templateUrl: 'views/detail.html',
                resolve: {
                    rel: function (Restangular, $route) {
                        return Restangular.one('relais', $route.current.params.pinName).get();
                    }
                }
            }).
            when('/new', {
                controller: CreateCtrl,
                templateUrl: 'views/detail.html'
            }).
            when('/edit-rule/:ruleName', {
                controller: RuleEditCtrl,
                templateUrl: 'views/rule-detail.html',
                resolve: {
                    rel: function (Restangular, $route) {
                        return Restangular.one('rule', $route.current.params.ruleName).get();
                    }
                }
            }).
            when('/new-rule', {
                controller: RuleCreateCtrl,
                templateUrl: 'views/rule-detail.html'
            })
            .otherwise({redirectTo: '/'});

        //RestangularProvider.setBaseUrl('http://192.168.1.105:3000');
        RestangularProvider.setBaseUrl('../');
        RestangularProvider.setDefaultHttpFields({withCredentials: false});

        /*RestangularProvider.setDefaultRequestParams({ apiKey: '4f847ad3e4b08a2eed5f3b54' })
         RestangularProvider.setRestangularFields({
         id: '_id.$oid'
         });

         RestangularProvider.setRequestInterceptor(function(elem, operation, what) {

         if (operation === 'put') {
         elem._id = undefined;
         return elem;
         }
         return elem;
         })*/

    });

function ListCtrl($scope, $log, Restangular) {
    $scope.relais = Restangular.all("relais").getList().$object;
    $scope.rules = Restangular.all("activation-rules").getList().$object;
    $scope.measurement = Restangular.one("temperature").get().$object;
    $scope.save = function (rel) {
        Restangular.all('relais').post(rel).then(function (relais) {
        });
    };
    $scope.saveRule = function (r) {
        Restangular.all('activation-rules').post(r).then(function (r) {
        });
    };
    $scope.destroyRule = function (r) {
        Restangular.all('activation-rules').delete(r).then(function (r) {
        });
    };
}

function CreateCtrl($scope, $location, Restangular) {
    $scope.rel = {pinName: "foo", pinState: "high"};

    $scope.save = function () {
        Restangular.all('relais').post($scope.rel).then(function (rel) {
            $location.path('/list');
        });
    }
}
function d(h, m) {
    var da = new Date();
    da.setHours(h);
    da.setMinutes(m);
    return da;
}
function newRule() {
    return {time: {from: d(0, 0), to: d(23, 59)}, rule: "(fn [m] :noop)"};
}

function RuleCreateCtrl($scope, $location, Restangular) {
    $scope.rule = newRule();

    $scope.saveRule = function () {
        Restangular.all('activation-rules').post($scope.rule).then(function (rule) {
            $location.path('/list');
        });
    }
}

function EditCtrl($scope, $location, Restangular, rel) {
    var original = rel;
    $scope.rel = Restangular.copy(original);


    $scope.isClean = function () {
        return angular.equals(original, $scope.rel);
    };

    $scope.destroy = function () {
        original.remove().then(function () {
            $location.path('/list');
        });
    };

    $scope.save = function () {
        $scope.rel.put().then(function () {
            $location.path('/');
        });
    };
}

function RuleEditCtrl($scope, $location, Restangular, rule) {
    var original = rule;
    $scope.rule = Restangular.copy(original);


    $scope.isClean = function () {
        return angular.equals(original, $scope.rule);
    };

    $scope.destroyRule = function () {
        original.remove().then(function () {
            $location.path('/list');
        });
    };

    $scope.saveRule = function () {
        $scope.rule.put().then(function () {
            $location.path('/');
        });
    };
}
