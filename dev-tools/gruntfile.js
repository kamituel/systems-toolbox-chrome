module.exports = function (grunt) {
    grunt.initConfig({
        pkg: grunt.file.readJSON('package.json'),

        watch: {
            less: {
                files: ['../src/devtools/less/*.less'],
                tasks: ['less'],
                options: {
                    spawn: true
                }
            }
        },
        less: {
            dev: {
                files: {
                    '../resources/devtools/styles/core.css': '../src/devtools/less/core.less'
                }
            }
        }
    });

    grunt.loadNpmTasks('grunt-contrib-less');
    grunt.loadNpmTasks('grunt-contrib-watch');
};
