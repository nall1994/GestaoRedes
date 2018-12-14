var express = require('express');
var router = express.Router();
var jsonfile = require('jsonfile')
var fs = require('fs')
var formidable = require('formidable')
const chartjs = require('chart.js')

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
  // A cena dos gráficos secalhar vai ter que ser feita com um ficheiro
  //javascript. Temos de apanhar um pedido submetido . fazer o post por ajax
  // receber os dados e fazer o plot do gráfico!
  res.render('graphic_view')
})

router.post('/get_chart_data',(req,res) => {
  var form = formidable.IncomingForm()
  form.parse(req,(erro,fields,files) => {
    if(fields.tipoInfo == '1') {
      //Isto está por dia! tentar pôr por hora depois!
      var path_to_interface = "../Database/" + fields.ipAgente + "_" + fields.portaAgente + "/interface" + fields.ifIndex + ".json"
      jsonfile.readFile(path_to_interface,"utf8",(erro,interface) => {
      var per_day = separateByDay(fields.data,interface.consultas)
      var per_day_average = calculateAverageOctets(per_day)
      console.log(per_day)
      res.jsonp(per_day)
      }) 
    } else {
      var path_to_agents_json = "../Database/agents.json"
      jsonfile.readFile(path_to_agents_json,"utf8",(erro,agentes) => {
        if(!erro) {
          var path_to_agent_interface= "../Database/" + fields.ipAgente + "_" + fields.portaAgente + "/interface"
          var number_interfaces = 0
          for(i in agentes) {
            if(agentes[i].ipAgente == fields.ipAgente && agentes[i].portaAgente) {
              number_interfaces = agentes[i].numInterfaces
              break
            }
          }
          var dados = new Object()
          dados.labels = new Array()
          dados.resultados = new Array()
          dados.label = 'Polling Time por Interface'
          dados.agente = fields.ipAgente + "_" + fields.portaAgente
          var data = retrieveAllData(path_to_agent_interface,dados,number_interfaces)
          console.log(data)
          res.jsonp(data)
        } else {
          res.render('error', {message: 'Não foi possível carregar o ficheiro de agentes!'})
        }
      })
    }
  })
  //Ir buscar os dados necessários para desenhar o gráfico!
})

function calculateAverageOctets(per_day) {
  //for(p in per_day)
}

function separateByDay(dataMinima,consultas) {
  //Ver como fazer isto. Tem que estar [{data: [octets]}]
  var octets_per_date = new Array()
  dataMinima = new Date(dataMinima)
  for(c in consultas) {
    var data_str = consultas[c].dataConsulta.split("T")[0]
    var data = new Date(data_str)
    if(data.getTime() >= dataMinima.getTime()){
      if(octets_per_date.some(obj => obj.hasOwnProperty(data_str))) {
        octets_per_date = updateArray(octets_per_date,data_str, consultas[c].difOctets)
      } else {
        var temp = new Object()
        temp[data_str] = [parseInt(consultas[c].difOctets)]
        octets_per_date.push(temp)
        console.log(octets_per_date)
        //octets_per_date.push({data_str: [parseInt(consultas[c].difOctets)]})
      }
    }
  }

  function updateArray(octets_per_date,data_str,difOctets) {
    //Aqui temos que verificar se o objecto corrente tem a chave que queremos!
      octets_per_date[data_str] = octets_per_date[data_str].push(parseInt(difOctets))
      return octets_per_date
  }
  /*var octets_per_date = new Object
  dataMinima = new Date(dataMinima)
    for(c in consultas) {
      var data_str = consultas[c].dataConsulta.split("T")[0]
      var data = new Date(data_str)
      if(data.getTime() >= dataMinima.getTime()) {
        if(data_str in octets_per_date) {
            octets_per_date[data_str].push(parseInt(consultas[c].difOctets))
        } else {
          octets_per_date[data_str] = new Array() 
          octets_per_date[data_str].push(parseInt(consultas[c].difOctets))
        }
      }
    }*/
    return octets_per_date
}

function retrieveAllData(path,dados,number_interfaces) {
  for(i=1; i<= number_interfaces;i++) {
    var path_aux = path + i + ".json"
    var json_data = jsonfile.readFileSync(path_aux,"utf8")
    dados.labels[i-1] = json_data.ifIndex
    dados.resultados[i-1] = parseInt(json_data.pollingTime)
  }
  return dados
}

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
