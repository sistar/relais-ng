'use strict';

angular.module('myApp', [
  'ngRoute',
  'restangular'
]).
config( function($routeProvider,	RestangularProvider) {
  $routeProvider.when('/', {
    controller:ListCtrl,
    templateUrl:'views/list.html'
  }).
  when('/edit/:pinName', {
    controller: EditCtrl,
    templateUrl:'views/detail.html',
    resolve: {
      rel: function(Restangular, $route) {
        return Restangular.one('relais',$route.current.params.pinName).get();
  }}}).
  when('/new', {
    controller:CreateCtrl,
    templateUrl:'views/detail.html'
  })
  .otherwise({redirectTo: '/'});

  //RestangularProvider.setBaseUrl('http://192.168.1.105:3000');
  RestangularProvider.setBaseUrl('http://localhost:3000');
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
function ListCtrl($scope, Restangular) {
   $scope.relais = Restangular.all("relais").getList().$object;

   $scope.save = function(rel) {
    Restangular.all('relais').post(rel).then(function(relais) {

    });
  }
}

function CreateCtrl($scope, $location, Restangular) {
  $scope.rel = {pinName: "foo",pinState: "high"}
  
  $scope.save = function() {
    Restangular.all('relais').post($scope.rel).then(function(rel) {
      $location.path('/list');
    });
  }
}

function EditCtrl($scope, $location, Restangular, rel) {
  var original = rel;
  $scope.rel = Restangular.copy(original);
  

  $scope.isClean = function() {
    return angular.equals(original, $scope.rel);
  }

  $scope.destroy = function() {
    original.remove().then(function() {
      $location.path('/list');
    });
  };

  $scope.save = function() {
    $scope.rel.put().then(function() {
      $location.path('/');
    });
  };
}
