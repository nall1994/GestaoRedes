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
      var path_to_interface = "../Database/" + fields.ipAgente + "_" + fields.portaAgente + "/interface" + fields.ifIndex + ".json"
      if(fields.tempUnit == '1') {
        jsonfile.readFile(path_to_interface,"utf8",(erro,interface) => {
          var per_day = separateByDay(fields.data,interface.consultas)
          var per_day_average = calculateAverageOctets(per_day)
          dados = new Object()
          dados.labels = new Array()
          dados.resultado = new Object()
          dados.resultado.data = new Array()
          for(k in per_day_average) {
            var pda = per_day_average[k]
            for(key in pda) {
              dados.labels.push(key)
              dados.resultado.data.push(JSON.stringify(pda[key]))
            } 
          }
          dados.resultado.label = interface.ifDescr
          dados.resultado.borderColor = '#c45850'
          dados.resultado.fill = false
          console.log(dados)
          res.jsonp(dados)
          })
      } else if(fields.tempUnit == '2') {
        //Põr labels e resultados por hora!
        jsonfile.readFile(path_to_interface,"utf8",(erro,interface) => {
          //Tenho que separar as consultas por data e hora, espécie de per_day mas fico com o primeiro elementos da hora!
          //Se fizer split por ":" e ir buscar o elemento 0 fico com isso!
          var per_day_hour = separateByDayHour(fields.data, interface.consultas)
          var per_day_hour_average = calculateAverageOctets(per_day_hour)
          dados = new Object()
          dados.labels = new Array()
          dados.resultado = new Object()
          dados.resultado.data = new Array()
          for(k in per_day_hour_average) {
            var pdha = per_day_hour_average[k]
            for(key in pdha) {
              dados.labels.push(key)
              dados.resultado.data.push(JSON.stringify(pdha[key]))
            }
          }
          dados.resultado.label = interface.ifDescr
          dados.resultado.borderColor = '#c45850'
          dados.resultado.fill = false
          res.jsonp(dados)
        })
      } else {
        jsonfile.readFile(path_to_interface,"utf8",(erro,interface) => {
          var per_consulta = separatePerConsulta(fields.data,interface.consultas)
          dados = new Object()
          dados.labels = new Array()
          dados.resultado = new Object()
          dados.resultado.data = new Array()
          console.log(per_consulta)
          for(k in per_consulta) {
            var pc = per_consulta[k]
            for(key in pc) {
              dados.labels.push(key)
              dados.resultado.data.push(JSON.stringify(pc[key][0]))
            }
          }
          dados.resultado.label = interface.ifDescr
          dados.resultado.borderColor = '#c45850'
          dados.resultado.fill = false
          res.jsonp(dados)
        })
      }      
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
          data.colors = new Array()
          data.colors = getColors(dados.labels.length)
          res.jsonp(data)
        } else {
          res.render('error', {message: 'Não foi possível carregar o ficheiro de agentes!'})
        }
      })
    }
  })
})

function getColors(numberOfColors) {
  var colors = ['#FF6633', '#FFB399', '#FF33FF', '#FFFF99', '#00B3E6', 
  '#E6B333', '#3366E6', '#999966', '#99FF99', '#B34D4D',
  '#80B300', '#809900', '#E6B3B3', '#6680B3', '#66991A', 
  '#FF99E6', '#CCFF1A', '#FF1A66', '#E6331A', '#33FFCC',
  '#66994D', '#B366CC', '#4D8000', '#B33300', '#CC80CC', 
  '#66664D', '#991AFF', '#E666FF', '#4DB3FF', '#1AB399',
  '#E666B3', '#33991A', '#CC9999', '#B3B31A', '#00E680', 
  '#4D8066', '#809980', '#E6FF80', '#1AFF33', '#999933',
  '#FF3380', '#CCCC00', '#66E64D', '#4D80CC', '#9900B3', 
'#E64D66', '#4DB380', '#FF4D4D', '#99E6E6', '#6666FF']
  var returnColors = new Array
  for(i = 0; i < numberOfColors; i++) {
    var rand = Math.floor(Math.random() * 49)
    returnColors.push(colors[rand])
  }
  return returnColors
}

function calculateAverageOctets(per_day) {
  //per_day => [{data: [difOctets]}]
  for(p in per_day) {
    var obj = per_day[p]
    for(property in obj) {
      var difOctetsArray = obj[property]
      obj[property] = calcAvg(difOctetsArray)
    }
    per_day[p] = obj
  }
  return per_day
}

function calcAvg(difOctetsArray) {
  var sum = 0
  for(i = 0; i < difOctetsArray.length; i++) {
    sum += difOctetsArray[i]
  }
  return Math.round(sum/difOctetsArray.length)
}

function separatePerConsulta(dataMinima,consultas) {
  var octets_per_date = new Array()
  dataMinima = new Date(dataMinima)
  for(c in consultas) {
    var date_parts = consultas[c].dataConsulta.split("T")
    var data_for_comparing = new Date(date_parts[0])
    var data_to_add = date_parts[0] + " " + date_parts[1].split(".")[0]
    if(data_for_comparing.getTime() >= dataMinima) {
      if(octets_per_date.some(obj => obj.hasOwnProperty(data_to_add))) {
        octets_per_date = updateArray(octets_per_date,data_to_add,consultas[c].difOctets)
      } else {
        var temp = new Object()
        temp[data_to_add] = [parseInt(consultas[c].difOctets)]
        octets_per_date.push(temp)
      }
    }
  }
  return octets_per_date
}

function separateByDay(dataMinima,consultas) {
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
      }
    }
  }
  return octets_per_date
}

function separateByDayHour(dataMinima,consultas) {
  var octets_per_date = new Array()
  dataMinima = new Date(dataMinima)
  for(c in consultas) {
    var data_for_comparing = new Date(consultas[c].dataConsulta.split("T")[0])
    var data_to_add_str = consultas[c].dataConsulta.split("T")[0] + " " +  consultas[c].dataConsulta.split("T")[1].split(":")[0] + "H"
    if(data_for_comparing.getTime() >= dataMinima.getTime()) {
      if(octets_per_date.some(obj => obj.hasOwnProperty(data_to_add_str))) {
        octets_per_date = updateArray(octets_per_date,data_to_add_str,consultas[c].difOctets)
      } else {
        var temp = new Object()
        temp[data_to_add_str] = [parseInt(consultas[c].difOctets)]
        octets_per_date.push(temp)
      }
    }
  }
  return octets_per_date
}

  function updateArray(octets_per_date,data_str,difOctets) {
    for(o in octets_per_date) {
      var obj = octets_per_date[o]
      if(obj[''+data_str] != undefined) {
        octets_per_date[o][''+data_str].push(parseInt(difOctets))
      }
    }
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
          for(i = 0; i < lines_to_write.length;i++) {
            if(i == lines_to_write.length - 1) {
              write += lines_to_write[i]
            } else {
              write += lines_to_write[i] + "\n"
            }
          }
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
          for(i = 0;i < lines_to_write.length;i++) {
            if(i == lines_to_write.length - 1) {
              write += lines_to_write[i]
            } else {
              write += lines_to_write[i] + "\n"
            }
          }
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
