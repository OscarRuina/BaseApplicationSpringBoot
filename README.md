# BaseApplicationSpringBoot

* Proyecto Base utilizando SpringBoot, que consta del manejo basico de usuarios, registro y envio de mails. Acceso a distintos puntos de acceso mediante roles utilizando Spring Security y JWT.
* Crear una base de datos MYSQL, comando para crear una bd: **create database nombre**;
* Configurar las variables de entorno para que el archivo application.properties las reconozca antes de iniciar la aplicacion:
  * DB_URL -> colocar la url de la base de datos.
  * DB_USERNAME -> colocar tu usuario de la base de datos.
  * DB_PASSWORD -> colocar tu password de la base de datos.
  * TOKEN_SECRET_KEY -> colocar un hash que sea tu secret key.
  * EMAIL -> colocar el mail que se encargara de enviar mails.
  * EMAIL_PASSWORD -> colocar el password de la cuenta del mail anterior.
* Ejecutar la aplicacion.
* En la carpeta de configurations/seeder una vez creadas las tablas de la bd se ejecuta la clase UsersSeeder que inicializa la bd con dos usuarios:
  * username: admin@gmail.com password: foo1234.
  * username: user@gmail.com password: foo1234.
* Probar endpoints utilizando **Postman**, localhost:8085/api/login -> utilizar las credenciales de los usuarios cargados. O Probar con **Swagger**. 
* Swagger : http://localhost:8085/api/swagger-ui/index.html#/
