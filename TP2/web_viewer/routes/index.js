var express = require('express');
var router = express.Router();
var jsonfile = require('jsonfile')
var fs = require('fs')

/* GET home page. */
router.get('/', function(req, res) {
  res.render('index')
});

router.get('/table_view', (req,res) => {
  res.render('table_view', {dados: undefined})
  //mostrar página de tabela (apresentar formulário de configuração)
})

router.post('/getConsultas', (req,res) => {
  var parametros = req.body
  var agente = parametros.ipAgente + "_" + parametros.portaAgente
  jsonfile.readFile("../Database/" + agente + "/interface" + parametros.ifIndex + ".json", (erro, interface) => {
    if(!erro) {
      var consultas = interface.consultas
      var new_consultas = new Array()
      var number_consultas = 0
      consultas.forEach(function(consulta) {
        var data = new Date(consulta.dataConsulta.split("T")[0])
        if(compareDates(data, new Date(parametros.data))){
          consulta.dataConsulta = formatDate(consulta.dataConsulta)
          consulta["ifIndex"] = parametros.ifIndex
          consulta["agente"] = agente
          new_consultas[number_consultas] = consulta
          number_consultas++
        }
      })
      res.render('table_view',{dados: new_consultas})
    } else {
      res.render('error',{message: "Erro a ler do ficheiro da interface escolhida!"})
    }
    
  }) 
})

router.get('/graphics_view', (req,res) => {
  //mostrar página de gráficos (apresentar formulário de configuração)
})

router.get('/interfaces_config',(req,res) => {
  res.render('config_view')
  //Configurar interfaces de um agente (apresentar formulário de configuração)
})

router.post('/configInterface',(req,res) => {
  var parameters = req.body
  var fileToChange = "../Database/" + parameters.ipAgente + "_" + parameters.portaAgente + "/interface" + parameters.ifIndex + ".json"
  var path_to_config = "../Database/config/" + parameters.ipAgente + "_" + parameters.portaAgente + "_interfaces.config"
  jsonfile.readFile(fileToChange,(erro,interface) => {
    if(!erro) {
      if(parameters.pollingType == "fixed") {
        interface.pollingTime = parameters.pollingTime
        jsonfile.writeFile(fileToChange,interface,erro => {
          res.render('error',{message: "Não foi possível escrever no ficheiro de interface"})
        })
        fs.readFile(path_to_config,"utf8",(erro,dados) => {
          var lines = dados.split("\n")
          var lines_to_write = new Array()
          var number_lines = 0
          lines.forEach(function(line) {
            if(line.split(":")[0] == parameters.ifIndex) {
              var newline = line.split(":")[0] + ":fixed:" + parameters.pollingTime
              lines_to_write[number_lines] = newline
              number_lines++
            } else {
              lines_to_write[number_lines] = line
              number_lines++
            }
          })
          var write = ""
          lines_to_write.forEach(function(line) {
            write += line + "\n"
          })
          fs.writeFile(path_to_config,write,"utf8", erro => {
            if(erro) res.render('error',{message: 'erro ao escrever no ficheiro de configuração'})
          })
        } )
      } else {
        fs.readFile(path_to_config,"utf8", (erro,dados) => {
          var lines = dados.split("\n")
          var lines_to_write = new Array()
          var number_lines = 0
          lines.forEach(function(line) {
            if(line.split(":")[0] == parameters.ifIndex) {
              var newline = line.split(":")[0] + ":dynamic"
              lines_to_write[number_lines] = newline
              number_lines++
            } else {
              lines_to_write[number_lines] = line
              number_lines++
            }
          })
          var write = ""
          lines_to_write.forEach(function(line) {
            write += line + "\n"
          })
          fs.writeFile(path_to_config,write,"utf8", erro => {
            res.render('error',{message: 'erro ao escrever no ficheiro de configuração'})
          })
        })
      }
    } else {
      res.render('error',{message: "Não foi possível ler o ficheiro de interface"})
    }
  })
  res.render('config_view')
})

function compareDates(dataConsulta, dataInserida) {
  if(dataConsulta.getTime() >= dataInserida.getTime()) return true
  else return false
}

function formatDate(date) {
  var date_and_time = date.split("T")
  var hours_minutes_seconds_and_millis = date_and_time[1].split(":")
  var returnDate = date_and_time[0] + " " + hours_minutes_seconds_and_millis[0]
  returnDate += ":" + hours_minutes_seconds_and_millis[1]
  returnDate += ":" + hours_minutes_seconds_and_millis[2].split(".")[0]
  return returnDate
}

module.exports = router;
